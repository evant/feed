@file:OptIn(ExperimentalMaterial3Api::class)

package me.tatarka.android.feed.app

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.android.feed.FeedConnection
import me.tatarka.android.feed.FeedPager
import me.tatarka.android.feed.FeedRemoteMediator
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.api.ItemRemoteMediator
import me.tatarka.android.feed.app.db.AppDatabase
import me.tatarka.android.feed.app.db.ItemEntity
import me.tatarka.android.feed.app.db.toItem
import me.tatarka.android.feed.compose.LazyPagingItems
import me.tatarka.android.feed.compose.collectAsLazyPagingItems
import me.tatarka.android.feed.compose.itemKey
import me.tatarka.android.feed.compose.rememberLazyListState

@OptIn(ExperimentalPagingApi::class)
class LoadFromStartRepository(
    private val api: ItemApi,
    private val db: AppDatabase,
    scope: CoroutineScope,
) {
    private val config = PagingConfig(pageSize = 25)

    private val remoteMediator = ItemRemoteMediator<Int, ItemEntity>(
        fetcher = { afterItem, beforeItem, size, replace ->
            try {
                Log.d(
                    "api",
                    "fetch: afterItem=${afterItem?.id}, beforeItem=${beforeItem?.id}, size=${size}, replace=${replace}"
                )
                val items = api.get(
                    minId = afterItem?.id,
                    maxId = beforeItem?.id,
                    size = size,
                )
                Log.d(
                    "api",
                    "fetch: itemCount=${items.size}, first=${items.firstOrNull()?.id}, last=${items.lastOrNull()?.id}"
                )
                db.itemDao.insertAll(items.map {
                    ItemEntity(
                        id = it.id,
                        text = it.text,
                    )
                }, replace = replace)
                val endOfPaginationReached = items.size < size
                FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = endOfPaginationReached)
            } catch (e: Exception) {
                Log.e("api", e.message, e)
                FeedRemoteMediator.LoadResult.Error(e)
            }
        },
        initialize = { db.itemDao.deleteAll() }
    )

    private val pager = FeedPager(
        config = config,
        remoteMediator = remoteMediator,
    ) {
        db.itemDao.itemsPaging()
    }

    val items = pager.flow.map { data ->
        data.map { entity ->
            entity.toItem(onCheckedChange = {
                scope.launch { db.itemDao.update(entity.id, it) }
            })
        }
    }
    val connection = pager.connection
}

class LoadFromStartVM(context: Application) : AndroidViewModel(context) {
    private val repo = LoadFromStartRepository(
        api = ItemApi(),
        db = AppDatabase.get(context),
        scope = viewModelScope,
    )

    val items = repo.items.cachedIn(viewModelScope)
    val connection = repo.connection
}

@Composable
fun LoadFromStatePage(modifier: Modifier = Modifier) {
    val vm = viewModel<LoadFromStartVM>()
    Page(
        modifier = modifier,
        items = vm.items.collectAsLazyPagingItems(vm.connection),
    )
}

@Composable
private fun Page(
    items: LazyPagingItems<Item>,
    modifier: Modifier = Modifier,
) {
    val listState by items.rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.eventFlow
            .filter { it == Lifecycle.Event.ON_RESUME }
            // skip initial load
            .drop(1)
            .collect {
                Log.d("list", "onResume: refreshInPlace")
                items.refreshInPlace()
            }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(Page.LoadFromStart.title) })
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallFloatingActionButton(onClick = { items.refreshInPlace() }) {
                    Text(
                        "Refresh in place",
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                ExtendedFloatingActionButton(onClick = { items.refresh() }) {
                    Text("Refresh")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier,
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
                if (!items.loadState.isIdle) {
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
    )
}
