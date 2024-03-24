package me.tatarka.android.feed

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeedLoader(
    initialLoadStart: Long,
    initialLoadEnd: Long,
    private val pageSize: Int,
    loadBefore: Boolean = true,
    loadAfter: Boolean = true,
) {
    private val mutex = Mutex()

    private val loadBeforeChannel = Channel<Unit>()
    private val loadAfterChannel = Channel<Unit>()

    private var finishedLoadingBefore = !loadBefore
    private var finishedLoadingAfter = !loadAfter

    private var currentLoadStart = initialLoadStart
    private var currentLoadEnd = initialLoadEnd

    suspend fun loadIn(window: LoadWindow) {
        mutex.withLock {
            if (!finishedLoadingBefore && window.start <= currentLoadStart) {
                loadBeforeChannel.send(Unit)
            }
            if (!finishedLoadingAfter && window.end >= currentLoadEnd) {
                loadAfterChannel.send(Unit)
            }
        }
    }

    suspend fun consumeLoadBefore(load: suspend (position: Long, count: Int) -> Int) {
        if (finishedLoadingBefore) return
        for (event in loadBeforeChannel) {
            val newLoadStart = (currentLoadStart - pageSize).coerceAtLeast(0)
            val count = (currentLoadStart - newLoadStart).toInt()
            if (newLoadStart == 0L) {
                finishedLoadingBefore = true
            }
            if (count > 0) {
                currentLoadStart = newLoadStart
                load(currentLoadStart, count)
            }
            if (finishedLoadingBefore) {
                break
            }
        }
    }

    suspend fun consumeLoadAfter(load: suspend (position: Long, count: Int) -> Int) {
        if (finishedLoadingAfter) return
        for (event in loadAfterChannel) {
            val loadedSize = load(currentLoadEnd, pageSize)
            if (loadedSize < pageSize) {
                finishedLoadingAfter = true
            }
            if (loadedSize > 0) {
                currentLoadEnd += loadedSize
            }
            if (finishedLoadingAfter) {
                break
            }
        }
    }
}