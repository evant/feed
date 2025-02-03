package me.tatarka.android.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * FeedPager builds upon [Pager] and presents an modified api with additional features.
 *
 * Like Pager, the same FeedPager instance should be reused within an instance of ViewModel. In
 * addition to the flow of paging data, the pager also has a connection which is used to pass
 * additional between the ui. For example in your ViewModel:
 *
 * ```
 * // create a FeedPager instance and store to a variable
 * private val pager = FeedPager(...)
 *
 * // provide the paging data flow and connection to the ui
 * val items = pager.flow.cacheIn(viewModelScope)
 * val connection = pager.connection
 * ```
 *
 * Each [PagingData] represents a snapshot of the backing paginated data. Updates to the backing
 * dataset should be represented by a new instance of [PagingData].
 *
 * [PagingSource.invalidate] will notify [Pager] that the backing dataset has been updated and a
 * new [PagingData] / [PagingSource] pair will be generated to represent an updated snapshot.
 *
 * Compose support is available in the `me.tatarka.android.feed:feed-compose` artifact.
 *
 * @param config configures the underlying [Pager].
 * @param loadFrom loads an initial offset for the items. This is a pair of an initialKey to fetch
 * content from and an itemKey to scroll the list to the correct position.
 * @param remoteMediator to incrementally load data from remote source into a local source. If you
 * set this then it's *highly* recommend for `PagingConfig(enablePlaceholders = true)` to prevent
 * some subtle paging bugs.
 * @param pagingSourceFactory creates a [PagingSource]. This should always return a new instance.
 */
@ExperimentalPagingApi
fun <Key : Any, Value : Any> FeedPager(
    config: PagingConfig,
    loadFrom: (suspend () -> InitialLoadState<Key>?)? = null,
    remoteMediator: FeedRemoteMediator<Key, Value>? = null,
    pagingSourceFactory: () -> PagingSource<Key, Value>
): FeedPager<Key, Value> {
    return if (loadFrom != null) {
        return FeedPager.LoadInitialKeyImpl(
            scope = CoroutineScope(Job()),
            config = config,
            loadFrom = loadFrom,
            remoteMediator = remoteMediator,
            pagingSourceFactory = pagingSourceFactory,
        )
    } else {
        FeedPager.ImmediateImpl(
            config = config,
            remoteMediator = remoteMediator,
            pagingSourceFactory = pagingSourceFactory,
        )
    }
}

sealed class FeedPager<Key : Any, Value : Any> {

    private val pagerGeneration = MutableStateFlow(0)

    /**
     * A cold [Flow] of [PagingData], which emits new instances of [PagingData] once they become
     * invalidated by [PagingSource.invalidate].
     *
     * NOTE: Instances of [PagingData] emitted by this [Flow] are not re-usable and cannot be
     * submitted multiple times. This is especially relevant for transforms such as
     * [Flow.combine][kotlinx.coroutines.flow.combine], which would replay the latest value
     * downstream. To ensure you get a new instance of [PagingData] for each downstream observer,
     * you should use the [androidx.paging.cachedIn] operator which multicasts the [Flow] in a way
     * that returns a new instance of [PagingData] with cached data pre-loaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: Flow<PagingData<Value>> = pagerGeneration.flatMapLatest {
        pagerFlow()
    }

    /**
     * This connection should be passed to the ui to allow additional information to be passed
     * between the pager and ui.
     */
    abstract val connection: FeedConnection

    protected abstract fun pagerFlow(): Flow<PagingData<Value>>

    protected fun reset() {
        pagerGeneration.update { it + 1 }
    }

    @ExperimentalPagingApi
    internal class ImmediateImpl<Key : Any, Value : Any>(
        config: PagingConfig,
        remoteMediator: FeedRemoteMediator<Key, Value>?,
        pagingSourceFactory: () -> PagingSource<Key, Value>,
    ) : FeedPager<Key, Value>() {

        private val remoteMediator = remoteMediator?.let { FeedRemoteMediatorAdapter(it) }

        private val pager = Pager(
            config = config,
            remoteMediator = this.remoteMediator,
            pagingSourceFactory = pagingSourceFactory,
        )

        override fun pagerFlow(): Flow<PagingData<Value>> = pager.flow

        override val connection: FeedConnection =
            object : FeedConnection, FeedConnection.FullRefresh {
                override suspend fun fullRefresh(refreshRequest: () -> Unit) {
                    val remote = this@ImmediateImpl.remoteMediator
                    if (remote != null) {
                        remote.fullRefresh(refreshRequest)
                    } else {
                        reset()
                    }
                }
            }
    }

    @ExperimentalPagingApi
    internal class LoadInitialKeyImpl<Key : Any, Value : Any>(
        scope: CoroutineScope,
        private val config: PagingConfig,
        loadFrom: suspend () -> InitialLoadState<Key>?,
        remoteMediator: FeedRemoteMediator<Key, Value>?,
        private val pagingSourceFactory: () -> PagingSource<Key, Value>,
    ) : FeedPager<Key, Value>() {

        private val remoteMediator = remoteMediator?.let { FeedRemoteMediatorAdapter(it) }

        private val state = scope.async(start = CoroutineStart.LAZY) {
            val item = loadFrom()
            val pager = createPager(initialKey = item?.initialKey)
            State(item, pager)
        }

        private fun createPager(initialKey: Key? = null): Pager<Key, Value> {
            return Pager(
                config = config,
                initialKey = initialKey,
                remoteMediator = this@LoadInitialKeyImpl.remoteMediator,
                pagingSourceFactory = pagingSourceFactory,
            )
        }

        override fun pagerFlow(): Flow<PagingData<Value>> =
            flow { emitAll(state.await().pager.flow) }

        @OptIn(ExperimentalCoroutinesApi::class)
        override val connection: FeedConnection =
            object : FeedConnection, FeedConnection.InitialItemKey, FeedConnection.FullRefresh {
                override val initialItemKey: Any?
                    get() = state.getCompleted().initial?.itemKey

                override suspend fun fullRefresh(refreshRequest: () -> Unit) {
                    state.getCompleted().apply {
                        initial = null
                        pager = createPager()
                    }
                    val remote = this@LoadInitialKeyImpl.remoteMediator
                    if (remote != null) {
                        remote.fullRefresh(refreshRequest)
                    } else {
                        reset()
                    }
                }
            }

        internal class State<Key : Any, Value : Any>(
            var initial: InitialLoadState<Key>?,
            var pager: Pager<Key, Value>
        )
    }
}

interface FeedConnection {
    /**
     * A [FeedConnection] that can handle loading from an initial item key. This is used internally
     * and should not be implemented by consumers.
     */
    interface InitialItemKey {
        val initialItemKey: Any?
    }

    /**
     * A [FeedConnection] that can handle fully refreshing the paging data. This is used internally
     * and should not be implemented by consumers.
     */
    interface FullRefresh {
        suspend fun fullRefresh(refreshRequest: () -> Unit)
    }
}

/**
 * A fixed [FeedConnection]. This can be used in tests where the actual [FeedPager] is
 * missing or mocked.
 */
fun FeedConnection(
    initialItemKey: Any? = null,
    onFullRefresh: (refreshRequest: () -> Unit) -> Unit = { it() },
): FeedConnection =
    object : FeedConnection, FeedConnection.InitialItemKey, FeedConnection.FullRefresh {
        var currentInitialItemKey = initialItemKey

        override val initialItemKey: Any?
            get() = currentInitialItemKey

        override suspend fun fullRefresh(refreshRequest: () -> Unit) {
            currentInitialItemKey = null
            onFullRefresh(refreshRequest)
        }
    }

/**
 * A [FeedConnection] that does nothing. This can be used in tests where the actual [FeedPager] is
 * missing or mocked.
 */
fun FeedConnection(): FeedConnection = EmptyFeedConnection

private val EmptyFeedConnection = object : FeedConnection, FeedConnection.FullRefresh {

    override suspend fun fullRefresh(refreshRequest: () -> Unit) {
        refreshRequest()
    }

}

/**
 * Represents the information required to initially load the list to a particular position.
 */
class InitialLoadState<Key : Any>(
    /**
     * The initial key to load data from. The load from this key should include the item you wish to
     * restore the scroll position to.
     */
    val initialKey: Key,
    /**
     * The itemKey used to determine which item in the ui to scroll to.
     */
    val itemKey: Any?
)