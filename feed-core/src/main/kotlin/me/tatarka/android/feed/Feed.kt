package me.tatarka.android.feed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedOptions(
    val pageSize: Int = 20,
    val prefetchDistance: Int = pageSize,
    val initialLoadSize: Int = pageSize * 3,
) {
    companion object {
        val Default = FeedOptions()
    }
}

fun <T> feed(
    localSource: Flow<LocalSource<T>>,
    remoteSource: RemoteSource,
    options: FeedOptions = FeedOptions.Default,
    initialOffset: suspend () -> Long = { 0 },
): Flow<FeedData<T>> = channelFlow {
    val initialOffsetVal = initialOffset()
    // center initial load around scroll position
    var currentLoadWindow = LoadWindow(
        start = (initialOffsetVal - options.initialLoadSize / 2).coerceAtLeast(0),
        size = options.initialLoadSize,
    )

    val remoteSourceEvent = RemoteMediatorEventSender(this@channelFlow).apply {
        with(remoteSource) {
            load(initialOffsetVal, options.pageSize)
        }
    }

    localSource.collectLatest { feedSource ->
        coroutineScope {
            remoteSourceEvent.refreshLoadWindow()?.let {
                currentLoadWindow = it
            }

            val initialLoadStart = currentLoadWindow.start
            var startingOffset = initialLoadStart
            val initialLoadSize = options.initialLoadSize
                // initial load needs to cover at least what's visible
                .coerceAtLeast(currentLoadWindow.size)
            val initialItems = feedSource.load(initialLoadStart, initialLoadSize)
            val initialLoadEnd = initialLoadStart + initialItems.size

            val loader = FeedLoader(
                initialLoadStart = initialLoadStart,
                initialLoadEnd = initialLoadEnd,
                pageSize = options.pageSize,
                loadBefore = initialLoadStart > 0,
                loadAfter = initialItems.size >= options.pageSize
            )

            val callbacks = object : FeedData.Callbacks {
                override fun updateVisibleWindow(
                    firstVisiblePosition: Int,
                    visibleItemCount: Int,
                ) {
                    val newLoadWindow = LoadWindow(
                        startingOffset = startingOffset,
                        firstVisiblePosition = firstVisiblePosition,
                        visibleItemCount = visibleItemCount,
                        prefetchDistance = options.prefetchDistance.coerceAtLeast(1)
                    )
                    val changed = currentLoadWindow != newLoadWindow
                    currentLoadWindow = newLoadWindow
                    if (changed) {
                        launch {
                            loader.loadIn(currentLoadWindow)
                        }
                    }
                }

                override suspend fun refresh() {
                    remoteSourceEvent.refresh(currentLoadWindow)
                }
            }

            if (initialItems.isEmpty()) {
                remoteSourceEvent.loadAfter()
                return@coroutineScope
            }

            send(
                FeedData(
                    type = FeedData.Type.Initial(
                        firstVisiblePosition = (initialOffsetVal - initialLoadStart).toInt(),
                    ),
                    items = initialItems,
                    callbacks = callbacks,
                )
            )

            launch {
                loader.consumeLoadBefore { position, count ->
                    val items = feedSource.load(position, count)
                    if (items.isNotEmpty()) {
                        startingOffset -= items.size
                        send(
                            FeedData(
                                type = FeedData.Type.Prepend,
                                items = items,
                                callbacks = callbacks,
                            )
                        )
                    }
                    items.size
                }
                remoteSourceEvent.loadBefore()
            }

            launch {
                loader.consumeLoadAfter { position, count ->
                    val items = feedSource.load(position, count)
                    if (items.isNotEmpty()) {
                        send(
                            FeedData(
                                type = FeedData.Type.Append,
                                items = items,
                                callbacks = callbacks,
                            )
                        )
                    }
                    items.size
                }
                remoteSourceEvent.loadAfter()
            }
        }

        awaitCancellation()
    }
}

private class Debouncer<T>(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val body: suspend (T) -> Unit
) {
    private var job: Job? = null

    operator fun invoke(arg: T) {
        job?.cancel()
        job = scope.launch {
            try {
                delay(delayMs)
            } finally {
                withContext(NonCancellable) {
                    body(arg)
                }
            }
        }
    }

    suspend fun flush() {
        job?.cancelAndJoin()
    }
}

private fun <T> CoroutineScope.debounce(
    delayMs: Long,
    body: suspend (T) -> Unit
): Debouncer<T> = Debouncer(this, delayMs, body)