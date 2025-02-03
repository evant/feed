@file:OptIn(ExperimentalPagingApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)

package me.tatarka.android.feed.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.android.feed.FeedConnection
import me.tatarka.android.feed.FeedPager
import me.tatarka.android.feed.FeedRemoteMediator
import me.tatarka.android.feed.InitialLoadState
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.api.ItemRemoteMediator
import me.tatarka.android.feed.app.db.AppDatabase
import me.tatarka.android.feed.app.db.ItemDao
import me.tatarka.android.feed.app.db.ItemEntity
import me.tatarka.android.feed.app.db.toItem
import me.tatarka.android.feed.compose.LazyPagingItems
import me.tatarka.android.feed.compose.collectAsLazyPagingItems
import me.tatarka.android.feed.compose.firstVisibleItemIndex
import me.tatarka.android.feed.compose.itemKey
import me.tatarka.android.feed.compose.rememberLazyListState

class SaveScrollPositionRepository(
    private val api: ItemApi,
    private val db: AppDatabase,
    scope: CoroutineScope,
    prefs: () -> SharedPreferences,
) {
    private val config = PagingConfig(pageSize = 25, enablePlaceholders = true)

    private val scrollPositionStore = SharedPrefsScrollPosition(
        dao = db.itemDao,
        prefs = lazy { prefs() },
        key = "scrollPosition1"
    )

    private val remoteMediator = ItemRemoteMediator<Int, ItemEntity>(
        fetch = { afterItem, beforeItem, size, replace ->
            try {
                val items = api.get(
                    minId = afterItem?.id,
                    maxId = beforeItem?.id,
                    size = size,
                )
                Log.d(
                    "api",
                    "fetch: itemCount=${items.size}, firstId=${items.firstOrNull()?.id}, lastId=${items.lastOrNull()?.id}, replace=${replace}"
                )
                if (replace) {
                    scrollPositionStore.clearScrollPosition()
                }
                db.itemDao.insertAll(items.map {
                    ItemEntity(
                        id = it.id,
                        text = it.text,
                    )
                }, replace = replace)
                FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = items.size < size)
            } catch (e: Exception) {
                Log.e("api", e.message, e)
                FeedRemoteMediator.LoadResult.Error(e)
            }
        },
    )

    private val pager = FeedPager(
        config = config,
        remoteMediator = remoteMediator,
        loadFrom = { scrollPositionStore.initialItem(config.initialLoadSize) }
    ) {
        db.itemDao.itemsPaging()
    }

    val items = pager.flow.map { data ->
        data.map { entity ->
            entity.toItem(onCheckedChange = {
                scope.launch {
                    db.itemDao.update(entity.id, it)
                }
            })
        }
    }

    val connection = pager.connection

    suspend fun updateScrollPosition(id: Long) {
        scrollPositionStore.saveScrollPosition(id)
    }

    class SharedPrefsScrollPosition(
        private val dao: ItemDao,
        private val prefs: Lazy<SharedPreferences>,
        private val key: String,
    ) {
        suspend fun initialItem(loadSize: Int): InitialLoadState<Int>? {
            val itemId = prefs.value.getLong(key, -1)
            if (itemId == -1L) return null
            val position = dao.offsetOf(itemId)
            return InitialLoadState(
                initialKey = (position - loadSize / 2).coerceAtLeast(0),
                itemKey = itemId,
            )
        }

        suspend fun saveScrollPosition(id: Long) {
            withContext(Dispatchers.IO) {
                prefs.value.edit(commit = true) {
                    putLong(key, id).also {
                        Log.d("prefs", "save id:$id")
                    }
                }
            }
        }

        suspend fun clearScrollPosition() {
            withContext(Dispatchers.IO) {
                prefs.value.edit(commit = true) {
                    remove(key)
                }
            }
        }
    }
}

class SaveScrollPositionVM(context: Application) : AndroidViewModel(context) {
    private val repo = SaveScrollPositionRepository(
        api = ItemApi(),
        db = AppDatabase.get(context),
        scope = viewModelScope,
        prefs = { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) },
    )

    val items = repo.items.cachedIn(viewModelScope)
    val connection = repo.connection

    suspend fun updateScrollPosition(id: Long) {
        repo.updateScrollPosition(id)
    }
}

@Composable
fun SaveScrollPositionPage(modifier: Modifier = Modifier) {
    val vm = viewModel<SaveScrollPositionVM>()
    Page(
        modifier = modifier,
        items = vm.items.collectAsLazyPagingItems(vm.connection),
        onSaveScrollPosition = vm::updateScrollPosition,
    )
}

@Composable
private fun Page(
    items: LazyPagingItems<Item>,
    onSaveScrollPosition: suspend (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState by items.rememberLazyListState(
        initialFirstVisibleItemIndex = { id ->
            if (id != null) items.itemSnapshotList.indexOfFirst { it?.id == id } else 0
        }
    )

    LaunchedEffect(Unit) {
        // refresh on initial load if there is existing data
        snapshotFlow { items.loadState.source }.first { it.isIdle }
        if (items.itemCount > 0) {
            Log.d("list", "onAttach: refreshInPlace")
            items.refreshInPlace()
        }

        // save scroll position
        snapshotFlow { listState.layoutInfo.firstVisibleItemIndex() }
            .debounce(500)
            .collectLatest { index ->
                val item = items.itemSnapshotList.getOrNull(index)
                if (item != null) {
                    onSaveScrollPosition(item.id)
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(Page.SaveScrollPosition.title) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { items.refresh() }) {
                Text("Refresh")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = modifier,
                state = listState,
                contentPadding = innerPadding,
            ) {
                items(items.itemCount, items.itemKey { it.id }) { index ->
                    val item = items[index]
                    if (item != null) {
                        MyListItem(
                            name = item.name,
                            checked = item.checked,
                            onCheckedChange = item.onCheckedChange,
                        )
                    } else {
                        PlaceholderListItem()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (
                    items.loadState.refresh is LoadState.Loading ||
                    items.loadState.append is LoadState.Loading
                ) {
                    if (items.itemCount == 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PagePreview() {
    Page(
        items = flowOf(PagingData.empty<Item>()).collectAsLazyPagingItems(FeedConnection()),
        onSaveScrollPosition = {},
    )
}
