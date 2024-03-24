package me.tatarka.android.feed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryDataStore<T> {
    private val _items = MutableStateFlow(emptyList<T>())
    val items: Flow<List<T>> get() = _items

    fun insertBefore(items: List<T>, refresh: Boolean = false) {
        _items.update { if (refresh) items else items + it }
    }

    fun insertAfter(items: List<T>, refresh: Boolean = false) {
        _items.update { if (refresh) items else it + items }
    }

    fun items(offset: Int, count: Int): List<T> = _items.value
        .asSequence()
        .drop(offset)
        .take(count)
        .toList()

    fun itemAt(offset: Long): T? = _items.value.getOrNull(offset.toInt())

    fun offsetOf(item: T): Long = _items.value.indexOf(item).toLong()
}

fun <T> InMemoryDataStore<T>.asFeedSource(): Flow<LocalSource<T>> = items.map {
    LocalSource { position, count -> items(position.toInt(), count) }
}

fun <T> InMemoryDataStore<T>.asScrollPositionStore(initialScrollPosition: Long = 0): ScrollPositionStore =
    InMemoryScrollPositionStore(this, initialScrollPosition)

class InMemoryRemoteSource<T>(
    private val source: InMemoryDataStore<T>,
) : RemoteSource {

    private val pageAfter = Channel<Pair<List<T>, Boolean>>(capacity = Channel.BUFFERED)
    private val pageBefore = Channel<Pair<List<T>, Boolean>>(capacity = Channel.BUFFERED)
    private val refreshContent = Channel<List<T>>(capacity = Channel.BUFFERED)

    private val awaitingLoadAfter = Channel<RemoteSource.LoadState>(capacity = 1)
    private val awaitingLoadAfterCanceled = Channel<RemoteSource.LoadState>(capacity = 1)

    suspend fun awaitOnLoadAfter(): RemoteSource.LoadState {
        return awaitingLoadAfter.receive()
    }

    suspend fun awaitOnLoadAfterCanceled(): RemoteSource.LoadState {
        return awaitingLoadAfterCanceled.receive()
    }

    fun pushPageAfter(items: List<T>, lastPage: Boolean = false) {
        pageAfter.trySend(items to lastPage)
    }

    fun pushPageBefore(items: List<T>, lastPage: Boolean = false) {
        pageBefore.trySend(items to lastPage)
    }

    fun setRefreshContent(items: List<T>) {
        refreshContent.trySend(items)
    }

    override suspend fun RemoteSource.Events.load(initialScrollPosition: Long, pageSize: Int) {
        onLoadAfter { loadState, channel ->
            try {
                awaitingLoadAfter.send(loadState)
                for (position in channel) {
                    val (page, lastPage) = pageAfter.receive()
                    source.insertAfter(page)
                    if (lastPage) break
                }
            } catch (e: CancellationException) {
                awaitingLoadAfterCanceled.send(loadState)
            }
        }
        onEachLoadBefore {
            val (page, lastPage) = pageBefore.receive()
            source.insertBefore(page)
            if (lastPage) complete()
        }
        onEachRefresh {
            val page = refreshContent.receive()
            source.insertAfter(page, refresh = true)
            0
        }
    }
}

class InMemoryScrollPositionStore<T>(
    private val dataStore: InMemoryDataStore<T>,
    private val initialScrollPosition: Long = 0,
): ScrollPositionStore {
    private var currentItem: T? = null

    override suspend fun load(refresh: Boolean): Long {
        if (currentItem == null || refresh) {
            currentItem = dataStore.itemAt(initialScrollPosition)
        }
        return currentItem?.let { dataStore.offsetOf(it) } ?: 0
    }

    override suspend fun save(position: Long) {
        currentItem = dataStore.itemAt(position)
    }
}