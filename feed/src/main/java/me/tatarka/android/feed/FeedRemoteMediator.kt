package me.tatarka.android.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.LoadState
import me.tatarka.android.feed.FeedRemoteMediator.LoadDirection

/**
 * An alternative api for [RemoteMediator] that handles some of it's shortcomings. Defines a set of
 * callbacks used to incrementally load data from a remote source into a local source wrapped by a
 * [PagingSource].
 *
 * A [FeedRemoteMediator] is registered by passing it to [FeedPager].
 *
 * [FeedRemoteMediator] allows hooking into the following events:
 * - stream initialization
 * - refresh signal driven from the UI
 * - [PagingSource] return s a [PagingSource.LoadResult] which signals a boundary condition, i.e,
 *   the most recent [PagingSource.LoadResult.Page] is the [LoadDirection.Prepend] or
 *   [LoadDirection.Append] direction has [PagingSource.LoadResult.Page.prevKey] or
 *   [PagingSource.LoadResult.Page.nextKey] set to `null` respectively.
 */
@ExperimentalPagingApi
interface FeedRemoteMediator<Key : Any, Value : Any> {

    /**
     * Callback triggered when Paging needs to request more data from a remote source due to a
     * boundary condition.
     *
     * It is the responsibility of this method to update the backing dataset and trigger
     * [PagingSource.invalidate].
     *
     * The runtime and result of this method defines the
     * [androidx.paging.CombinedLoadStates.mediator] prepend/append behavior.
     *
     * This method is never called concurrently *unless* [FeedPager.flow] has multiple collectors.
     *
     * @param direction The direction in which the end of pagination was reached.
     * @param state A copy of the state including the list of pages held in memory of the currently
     * presented [PagingData] at the time of starting the load. E.g. for load [LoadDirection.Append]
     * you can use the page or item at the end of the input for what to load from the network.
     *
     * @return [LoadResult] signifying what [LoadState] to be passed to the UI, and whether there's
     * more data available.
     */
    suspend fun load(direction: LoadDirection, state: PagingState<Key, Value>): LoadResult

    /**
     * Callback triggered when Paging needs to request more data from a source due to a refresh
     * signal sent from the UI.
     *
     * It is the responsibility of this method to update the backing dataset and trigger
     * [PagingSource.invalidate].
     *
     * The runtime and result of this method defines the
     * [androidx.paging.CombinedLoadStates.mediator] refresh behavior.
     *
     * This method is never called concurrently *unless* [FeedPager.flow] has multiple collectors.
     *
     * @param state A copy of the state including the list of pages held in memory of the currently
     * presented [PagingData] at the time of starting the refresh. Can be used to ensure the refresh
     * covers the presented items to prevent the UI from shifting. If a full refresh is requested
     * this state will be empty.
     * @return [RefreshResult] signifying what [LoadState] to be passed to the UI, and whether
     * there's more data available in either direction.
     */
    suspend fun refresh(state: PagingState<Key, Value>): RefreshResult

    /**
     * Callback fired during initialization of a [PagingData] stream, before initial load. This
     * function runs to completion before any loading is performed. This method can be used to clear
     * out stale data before it can be presented to the UI.
     */
    suspend fun initialize() {}

    /**
     * The direction to load at a boundary condition.
     */
    enum class LoadDirection {
        /**
         * Load at the start
         */
        Prepend,

        /**
         * Load at the end
         */
        Append,
    }

    /**
     * Returns type of [load], which determines [LoadState].
     */
    sealed class LoadResult {
        /**
         * Recoverable error that can be retried, sets the [LoadState] to [LoadState.Error].
         */
        class Error(val throwable: Throwable) : LoadResult()

        /**
         * Success signaling that [LoadState] should be set to [LoadState.NotLoading] if
         * [endOfPaginationReached] is `true`, otherwise [LoadState] is kept at [LoadState.Loading]
         * to await invalidation.
         *
         * NOTE: It is the responsibility of [load] to update the backing dataset and trigger
         * [PagingSource.invalidate].
         */
        class Success(val endOfPaginationReached: Boolean) : LoadResult()
    }

    /**
     * Returns type of [refresh], which determines [LoadState].
     */
    sealed class RefreshResult {
        /**
         * Recoverable error that can be retried, sets the [LoadState] to [LoadState.Error].
         */
        class Error(val throwable: Throwable) : RefreshResult()

        /**
         * Success signaling that [LoadState] should be set to [LoadState.NotLoading] if
         * [endOfPaginationReached] is `true`, otherwise [LoadState] is kept at [LoadState.Loading]
         * to await invalidation.
         *
         * NOTE: It is the responsibility of [load] to update the backing dataset and trigger
         * [PagingSource.invalidate].
         */
        class Success(
            val endOfPrependReached: Boolean,
            val endOfAppendReached: Boolean,
        ) : RefreshResult()
    }
}

@ExperimentalPagingApi
fun <Key : Any, Value : Any> FeedRemoteMediator<Key, Value>.asPagingRemoteMediator(): RemoteMediator<Key, Value> {
    return FeedRemoteMediatorAdapter(this)
}