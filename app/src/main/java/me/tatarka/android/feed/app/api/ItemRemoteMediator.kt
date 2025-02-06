package me.tatarka.android.feed.app.api

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.paging.PagingState
import me.tatarka.android.feed.FeedRemoteMediator
import me.tatarka.android.feed.FeedRemoteMediator.LoadResult

/**
 * A [FeedRemoteMediator] that fetches before and after particular items in the list.
 *
 * @param fetcher Responsible for fetching items and storing them.
 * @param order The ordering of displayed items. [ItemOrder.Ascending] means the initial fetch will
 * be at the top of the list and items will be appended. [ItemOrder.Descending] means the initial
 * fetch will be at the bottom of the list and items will be prepended.
 * @param initialize Called on [FeedRemoteMediator.initialize]. May be used to clear stale data.
 */
@ExperimentalPagingApi
class ItemRemoteMediator<Key : Any, Item : Any>(
    private val fetcher: Fetcher<Item>,
    private val order: ItemOrder = ItemOrder.Ascending,
    private val initialize: suspend () -> Unit = {},
) : FeedRemoteMediator<Key, Item> {

    fun interface Fetcher<Item> {
        /**
         * This method should fetch items and store them, invalidating the
         * [androidx.paging.DataSource]. You should handle the following scenarios:
         * 1. If either [afterItem] or [beforeItem] is not null, should fetch [size] items from the
         *    provided item.
         * 2. If both [afterItem] and [beforeItem] are null, should fetch [size] items from the
         *    initial starting position.
         *
         * @param afterItem Fetch items after this item, typically by using it's id.
         * @param beforeItem Fetch items before this item, typically by using it's id.
         * @param size The number of items to fetch. It's ok to fetch more or less than this.
         * However, you should always fetch enough items to fill the viewport to prevent the UI from
         * jumping.
         * @param replace If this is true you should clear any items that are currently stored. This
         * indicates a refresh.
         *
         * @return A [LoadResult] indicating if the fetch was successful and if it has reached the
         * end of what can be fetched.
         */
        suspend fun fetch(
            afterItem: Item?,
            beforeItem: Item?,
            size: Int,
            replace: Boolean
        ): FeedRemoteMediator.LoadResult
    }

    override suspend fun load(
        direction: FeedRemoteMediator.LoadDirection,
        state: PagingState<Key, Item>
    ): FeedRemoteMediator.LoadResult {
        return when (direction) {
            FeedRemoteMediator.LoadDirection.Prepend -> {
                val item = state.firstItemOrNull()
                fetch(
                    firstItem = null,
                    lastItem = item,
                    size = state.config.pageSize,
                    replace = false
                )
            }

            FeedRemoteMediator.LoadDirection.Append -> {
                val item = state.lastItemOrNull()
                fetch(
                    firstItem = item,
                    lastItem = null,
                    size = state.config.pageSize,
                    replace = false
                )
            }
        }
    }

    override suspend fun refresh(state: PagingState<Key, Item>): FeedRemoteMediator.RefreshResult {
        val anchorPosition = state.anchorPosition
        val loadSize = state.config.initialLoadSize
        return if (anchorPosition == null) {
            val result = fetch(
                firstItem = null,
                lastItem = null,
                size = loadSize,
                replace = true,
            )
            result.mapToRefreshResult {
                when (order) {
                    ItemOrder.Ascending -> FeedRemoteMediator.RefreshResult.Success(
                        endOfPrependReached = true,
                        endOfAppendReached = it.endOfPaginationReached,
                    )

                    ItemOrder.Descending -> FeedRemoteMediator.RefreshResult.Success(
                        endOfPrependReached = it.endOfPaginationReached,
                        endOfAppendReached = true,
                    )
                }
            }
        } else {
            val loadDirection = calculateLoadDirection(state, anchorPosition, loadSize)
            when (loadDirection) {
                FeedRemoteMediator.LoadDirection.Prepend -> {
                    val item = state.closestItemToPosition(anchorPosition + loadSize / 2)
                    val result = fetch(
                        firstItem = null,
                        lastItem = item,
                        size = loadSize,
                        replace = true
                    )
                    result.mapToRefreshResult {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = it.endOfPaginationReached,
                            endOfAppendReached = false,
                        )
                    }
                }

                FeedRemoteMediator.LoadDirection.Append -> {
                    val item = state.closestItemToPosition(anchorPosition - loadSize / 2)
                    val result = fetch(
                        firstItem = item,
                        lastItem = null,
                        size = loadSize,
                        replace = true
                    )
                    result.mapToRefreshResult {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = false,
                            endOfAppendReached = it.endOfPaginationReached,
                        )
                    }
                }
            }
        }
    }

    private fun calculateLoadDirection(
        state: PagingState<Key, Item>,
        anchorPosition: Int,
        loadSize: Int,
    ): FeedRemoteMediator.LoadDirection {
        val firstPosition = anchorPosition - loadSize / 2
        val lastPosition = anchorPosition + loadSize / 2
        val firstIndex = state.pages.firstOrNull()?.itemsBefore
            ?.takeIf { it != PagingSource.LoadResult.Page.COUNT_UNDEFINED }
            ?: 0
        val lastIndex = firstIndex +
                (state.pages.sumOf { it.data.size } - 1) +
                (state.pages.lastOrNull()?.itemsAfter
                    ?.takeIf { it != PagingSource.LoadResult.Page.COUNT_UNDEFINED }
                    ?: 0)

        val firstDistance = firstPosition - firstIndex
        val lastDistance = lastIndex - lastPosition

        // If only one of first or last is valid, return that direction
        if (firstDistance >= 0 && lastDistance < 0) {
            return FeedRemoteMediator.LoadDirection.Append
        }
        if (lastDistance >= 0 && firstDistance < 0) {
            return FeedRemoteMediator.LoadDirection.Prepend
        }

        // If distances are the same, load based on item order
        if (firstDistance == lastDistance) {
            return when (order) {
                ItemOrder.Ascending -> FeedRemoteMediator.LoadDirection.Append
                ItemOrder.Descending -> FeedRemoteMediator.LoadDirection.Prepend
            }
        }

        // Pick the one furthest away
        return if (firstDistance > lastDistance) FeedRemoteMediator.LoadDirection.Append
        else FeedRemoteMediator.LoadDirection.Prepend
    }

    private suspend fun fetch(
        firstItem: Item?,
        lastItem: Item?,
        size: Int,
        replace: Boolean
    ): FeedRemoteMediator.LoadResult {
        return when (order) {
            ItemOrder.Ascending -> fetcher.fetch(
                afterItem = firstItem,
                beforeItem = lastItem,
                size = size,
                replace = replace,
            )

            ItemOrder.Descending -> fetcher.fetch(
                afterItem = lastItem,
                beforeItem = firstItem,
                size = size,
                replace = replace,
            )
        }
    }

    private inline fun FeedRemoteMediator.LoadResult.mapToRefreshResult(
        transform: (FeedRemoteMediator.LoadResult.Success) -> FeedRemoteMediator.RefreshResult.Success
    ): FeedRemoteMediator.RefreshResult = when (this) {
        is FeedRemoteMediator.LoadResult.Error -> FeedRemoteMediator.RefreshResult.Error(throwable)
        is FeedRemoteMediator.LoadResult.Success -> transform(this)
    }

    private fun FeedRemoteMediator.LoadResult.toRefreshResult(
        endOfOpposingReached: Boolean,
    ): FeedRemoteMediator.RefreshResult {
        return when (this) {
            is FeedRemoteMediator.LoadResult.Error ->
                FeedRemoteMediator.RefreshResult.Error(throwable)

            is FeedRemoteMediator.LoadResult.Success -> {
                when (order) {
                    ItemOrder.Ascending -> {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = endOfOpposingReached,
                            endOfAppendReached = endOfPaginationReached,
                        )
                    }

                    ItemOrder.Descending -> {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = endOfPaginationReached,
                            endOfAppendReached = endOfOpposingReached,
                        )
                    }
                }
            }
        }
    }

    override suspend fun initialize() {
        initialize.invoke()
    }

    enum class ItemOrder {
        Ascending,
        Descending,
    }
}