package me.tatarka.android.feed.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.android.feed.FeedConnection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class LazyPagingItems<T : Any>
internal constructor(
    private val flow: Flow<PagingData<T>>,
    private val connection: FeedConnection,
    private val loadGeneration: LoadGeneration,
) {
    private var anchorSet = false
    private var pendingRefresh = false

    private val scope = CoroutineScope(Dispatchers.Main)

    private val pagingDataPresenter = object : PagingDataPresenter<T>(
        cachedPagingData =
            if (flow is SharedFlow<PagingData<T>>) flow.replayCache.firstOrNull() else null
    ) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            updateItemSnapshotList()
        }

    }

    internal val initialItemKey: Any?
        get() = (connection as? FeedConnection.InitialItemKey)?.initialItemKey

    internal val loadKey by derivedStateOf {
        if (!loadState.source.isIdle && itemCount == 0) {
            null
        } else {
            loadGeneration.currentGeneration
        }
    }

    var itemSnapshotList by mutableStateOf(
        pagingDataPresenter.snapshot()
    )
        private set

    val itemCount: Int get() = itemSnapshotList.size

    private fun updateItemSnapshotList() {
        itemSnapshotList = pagingDataPresenter.snapshot()
    }

    operator fun get(index: Int): T? {
        pagingDataPresenter[index] // this registers the value load
        anchorSet = true
        if (pendingRefresh) {
            pendingRefresh = false
            refreshJob?.cancel()
            refreshJob = scope.launch {
                pagingDataPresenter.refresh()
            }
        }
        return itemSnapshotList[index]
    }

    fun peek(index: Int): T? {
        return itemSnapshotList[index]
    }

    fun retry() {
        pagingDataPresenter.retry()
    }

    private var refreshJob: Job? = null

    fun refresh() {
        require(connection is FeedConnection.FullRefresh) {
            "FeedConnection does not implement full refresh. Did you create your pager with FeedPager?"
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            connection.fullRefresh(pagingDataPresenter::refresh)
            loadGeneration.startLoading()
        }
    }

    fun refreshInPlace() {
        if (anchorSet) {
            pagingDataPresenter.refresh()
        } else {
            pendingRefresh = true
        }
    }

    var loadState: CombinedLoadStates by mutableStateOf(
        pagingDataPresenter.loadStateFlow.value
            ?: CombinedLoadStates(
                refresh = InitialLoadStates.refresh,
                prepend = InitialLoadStates.prepend,
                append = InitialLoadStates.append,
                source = InitialLoadStates
            )
    )
        private set

    internal suspend fun collectLoadState() {
        pagingDataPresenter.loadStateFlow.filterNotNull().collect {
            loadState = it
            if (loadGeneration.loading && it.refresh is LoadState.NotLoading) {
                loadGeneration.endLoading()
            }
        }
    }

    internal suspend fun collectPagingData() {
        flow.collectLatest {
            pagingDataPresenter.collectFrom(it)
        }
    }
}

private val IncompleteLoadState = LoadState.NotLoading(false)
private val InitialLoadStates = LoadStates(
    LoadState.Loading,
    IncompleteLoadState,
    IncompleteLoadState
)

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(
    connection: FeedConnection,
    context: CoroutineContext = EmptyCoroutineContext
): LazyPagingItems<T> {

    val loadGeneration = rememberLoadGeneration()
    val lazyPagingItems = remember(this, connection) {
        LazyPagingItems(this, connection, loadGeneration)
    }

    LaunchedEffect(lazyPagingItems) {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectPagingData()
        } else {
            withContext(context) {
                lazyPagingItems.collectPagingData()
            }
        }
    }

    LaunchedEffect(lazyPagingItems) {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectLoadState()
        } else {
            withContext(context) {
                lazyPagingItems.collectLoadState()
            }
        }
    }

    return lazyPagingItems
}
