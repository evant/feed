package me.tatarka.android.feed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.launch

typealias OnRemoteSourceLoad = suspend (loadState: RemoteSource.LoadState, ReceiveChannel<Unit>) -> Unit

typealias OnRemoteSourceRefresh = suspend (LoadWindow) -> Long

fun interface RemoteSource {
    suspend fun Events.load(initialScrollPosition: Long, pageSize: Int)

    interface Events : CoroutineScope {
        fun onLoadAfter(block: OnRemoteSourceLoad)

        fun onLoadBefore(block: OnRemoteSourceLoad)

        fun onEachRefresh(block: OnRemoteSourceRefresh)
    }

    enum class LoadState {
        Initial, Refresh, Retry
    }
}

fun RemoteSource.Events.onEachLoadAfter(block: suspend CompleteScope.() -> Unit) {
    onLoadAfter { _, channel -> channel.completeForEach { block() } }
}

fun RemoteSource.Events.onEachLoadBefore(block: suspend CompleteScope.() -> Unit) {
    onLoadBefore { _, channel -> channel.completeForEach{ block() } }
}

private suspend fun <T> ReceiveChannel<T>.completeForEach(block: suspend CompleteScope.(T) -> Unit) {
    val completeScope = CompleteScopeImpl()
    for (value in this) {
        completeScope.block(value)
        if (completeScope.completeCalled) break
    }
}

interface CompleteScope {
    fun complete()
}

private class CompleteScopeImpl : CompleteScope {
    var completeCalled = false

    override fun complete() {
        completeCalled = true
    }
}

class EmptyRemoteSource : RemoteSource {
    override suspend fun RemoteSource.Events.load(
        initialScrollPosition: Long,
        pageSize: Int,
    ) {
        // loads nothing
    }
}

class RemoteMediatorEventSender(scope: CoroutineScope) : RemoteSource.Events,
    CoroutineScope by scope {

    private var loadAfterChannel = Channel<Unit>()
    private var loadBeforeChannel = Channel<Unit>()
    private var refreshResultChannel = Channel<LoadWindow>(1)

    private var loadBeforeCallback: OnRemoteSourceLoad? = null
    private var loadAfterCallback: OnRemoteSourceLoad? = null
    private var refreshCallback: OnRemoteSourceRefresh? = null
    private var refreshSent = false

    override fun onLoadAfter(block: OnRemoteSourceLoad) {
        loadAfterCallback = block
        launch { loadAfterChannel.consume { block(RemoteSource.LoadState.Initial, this) } }
    }

    override fun onLoadBefore(block: OnRemoteSourceLoad) {
        loadBeforeCallback = block
        launch { loadBeforeChannel.consume { block(RemoteSource.LoadState.Initial, this) } }
    }

    override fun onEachRefresh(block: suspend (LoadWindow) -> Long) {
        refreshCallback = block
    }

    suspend fun loadBefore() {
        if (loadBeforeCallback == null) return
        try {
            loadBeforeChannel.send(Unit)
        } catch (e: ClosedSendChannelException) {
            // ignore
        }
    }

    suspend fun loadAfter() {
        if (loadAfterCallback == null) return
        try {
            loadAfterChannel.send(Unit)
        } catch (e: ClosedSendChannelException) {
            // ignore
        }
    }

    suspend fun refresh(loadWindow: LoadWindow) {
        refreshCallback?.let { block ->
            refreshSent = true
            onRefresh()
            val newStart = block(loadWindow)
            refreshResultChannel.send(
                LoadWindow(
                    start = newStart,
                    size = loadWindow.size
                )
            )
        }
    }

    private fun onRefresh() {
        loadBeforeChannel.cancel()
        loadAfterChannel.cancel()
        loadBeforeChannel = Channel()
        loadAfterChannel = Channel()
        loadBeforeCallback?.let { block ->
            launch { loadBeforeChannel.consume { block(RemoteSource.LoadState.Refresh, this) } }
        }
        loadAfterCallback?.let { block ->
            launch { loadAfterChannel.consume { block(RemoteSource.LoadState.Refresh, this) } }
        }
    }

    suspend fun refreshLoadWindow(): LoadWindow? {
        return if (refreshSent) {
            refreshSent = false
            refreshResultChannel.receive()
        } else {
            null
        }
    }
}