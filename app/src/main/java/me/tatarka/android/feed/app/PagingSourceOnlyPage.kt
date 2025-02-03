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
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.tatarka.android.feed.FeedConnection
import me.tatarka.android.feed.FeedPager
import me.tatarka.android.feed.InitialLoadState
import me.tatarka.android.feed.app.api.ApiItem
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.api.toItem
import me.tatarka.android.feed.compose.LazyPagingItems
import me.tatarka.android.feed.compose.collectAsLazyPagingItems
import me.tatarka.android.feed.compose.firstVisibleItemIndex
import me.tatarka.android.feed.compose.itemKey
import me.tatarka.android.feed.compose.rememberLazyListState

class PagingSourceOnlyRepository(
    private val api: ItemApi,
    prefs: () -> SharedPreferences,
) {
    private val config = PagingConfig(pageSize = 25, enablePlaceholders = true)

    private val scrollPositionStore = SharedPrefsScrollPosition(
        prefs = lazy { prefs() },
        key = "scrollPosition2"
    )

    class MyPagingSource(
        private val api: ItemApi,
    ) : PagingSource<Long, ApiItem>() {
        override fun getRefreshKey(state: PagingState<Long, ApiItem>): Long? {
            return state.anchorPosition?.let {
                val startPosition = it - state.config.initialLoadSize / 2
                if (startPosition < 0) null else state.closestItemToPosition(startPosition)?.id
            }
        }

        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, ApiItem> {
            val minId: Long?
            val maxId: Long?
            when (params) {
                is LoadParams.Refresh -> {
                    minId = params.key
                    maxId = null
                }

                is LoadParams.Prepend -> {
                    minId = params.key - params.loadSize
                    maxId = params.key
                }

                is LoadParams.Append -> {
                    minId = params.key
                    maxId = params.key + params.loadSize
                }
            }
            Log.d(
                "api",
                "fetch: itemCount=${params.loadSize}, firstId=${minId}, lastId=${maxId}, replace=${params is LoadParams.Refresh}"
            )
            return try {
                val items = api.get(
                    minId = minId,
                    maxId = maxId,
                    size = params.loadSize
                )
                LoadResult.Page(
                    data = items,
                    prevKey = if (minId != null) items.firstOrNull()?.id else null,
                    nextKey = items.lastOrNull()?.id,
                )
            } catch (e: Exception) {
                Log.e("api", e.message, e)
                LoadResult.Error(e)
            }
        }
    }

    private val pager = FeedPager(
        config = config,
        loadFrom = { scrollPositionStore.initialItem(config.initialLoadSize) }
    ) {
        MyPagingSource(api)
    }

    val items = pager.flow.map { data ->
        data.map { entity -> entity.toItem() }
    }

    val connection = pager.connection

    suspend fun updateScrollPosition(id: Long) {
        scrollPositionStore.saveScrollPosition(id)
    }

    class SharedPrefsScrollPosition(
        private val prefs: Lazy<SharedPreferences>,
        private val key: String,
    ) {
        suspend fun initialItem(loadSize: Int): InitialLoadState<Long>? {
            return withContext(Dispatchers.IO) {
                val itemId = prefs.value.getLong(key, -1)
                if (itemId == -1L) return@withContext null
                InitialLoadState(
                    initialKey = (itemId - loadSize / 2L).coerceAtLeast(0L),
                    itemKey = itemId,
                )
            }
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

class PagingSourceOnlyVM(context: Application) : AndroidViewModel(context) {
    private val repo = PagingSourceOnlyRepository(
        api = ItemApi(),
        prefs = { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) },
    )

    val items = repo.items.cachedIn(viewModelScope)
    val connection = repo.connection

    suspend fun updateScrollPosition(id: Long) {
        repo.updateScrollPosition(id)
    }
}

@Composable
fun PagingSourceOnlyPage(modifier: Modifier = Modifier) {
    val vm = viewModel<PagingSourceOnlyVM>()
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
            TopAppBar(title = { Text(Page.PagingSourceOnly.title) })
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
