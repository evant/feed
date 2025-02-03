package me.tatarka.android.feed.app.api

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.paging.PagingState
import me.tatarka.android.feed.FeedRemoteMediator

@ExperimentalPagingApi
class ItemRemoteMediator<Key : Any, Item : Any>(
    private val fetch: Fetch<Item>,
    private val order: ItemOrder = ItemOrder.Ascending,
    private val initialize: suspend () -> Unit = {},
) : FeedRemoteMediator<Key, Item> {

    fun interface Fetch<Item> {
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
        val loadSize = if (state.isEmpty()) state.config.initialLoadSize else state.config.pageSize
        return when (direction) {
            FeedRemoteMediator.LoadDirection.Prepend -> {
                val item = state.firstItemOrNull()
                if (item == null && order == ItemOrder.Ascending) {
                    // Since we started with no data, then append will already be the freshest, we
                    // don't need to prepend
                    return FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = true)
                }
                when (order) {
                    ItemOrder.Ascending -> {
                        fetch.fetch(
                            afterItem = null,
                            beforeItem = item,
                            size = loadSize,
                            replace = false
                        )
                    }

                    ItemOrder.Descending -> {
                        fetch.fetch(
                            afterItem = item,
                            beforeItem = null,
                            size = loadSize,
                            replace = false
                        )
                    }
                }
            }

            FeedRemoteMediator.LoadDirection.Append -> {
                val item = state.lastItemOrNull()
                if (item == null && order == ItemOrder.Descending) {
                    // Since we started with no data, then prepend will already be the freshest, we
                    // don't need to append
                    return FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = true)
                }
                when (order) {
                    ItemOrder.Ascending -> {
                        fetch.fetch(
                            afterItem = item,
                            beforeItem = null,
                            size = loadSize,
                            replace = false
                        )
                    }

                    ItemOrder.Descending -> {
                        fetch.fetch(
                            afterItem = null,
                            beforeItem = item,
                            size = loadSize,
                            replace = false
                        )
                    }
                }
            }
        }
    }

    override suspend fun refresh(state: PagingState<Key, Item>): FeedRemoteMediator.RefreshResult {
        val loadSize = state.config.initialLoadSize
        var firstItem: Item? = null
        var lastItem: Item? = null
        state.anchorPosition?.let {
            firstItem = state.firstItemAtPositionOrNull(it - loadSize / 2)
            lastItem = state.lastItemAtPositionOrNull(it + loadSize / 2)
        }
        return when (order) {
            ItemOrder.Ascending -> {
                val result = fetch.fetch(
                    afterItem = firstItem,
                    beforeItem = lastItem,
                    size = loadSize,
                    replace = true
                )
                when (result) {
                    is FeedRemoteMediator.LoadResult.Error ->
                        FeedRemoteMediator.RefreshResult.Error(result.throwable)

                    is FeedRemoteMediator.LoadResult.Success -> {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = lastItem != null && result.endOfPaginationReached,
                            endOfAppendReached = firstItem != null && result.endOfPaginationReached,
                        )
                    }
                }
            }

            ItemOrder.Descending -> {
                val result = fetch.fetch(
                    afterItem = lastItem,
                    beforeItem = firstItem,
                    size = loadSize,
                    replace = true
                )
                when (result) {
                    is FeedRemoteMediator.LoadResult.Error ->
                        FeedRemoteMediator.RefreshResult.Error(result.throwable)

                    is FeedRemoteMediator.LoadResult.Success -> {
                        FeedRemoteMediator.RefreshResult.Success(
                            endOfPrependReached = lastItem != null && result.endOfPaginationReached,
                            endOfAppendReached = firstItem != null && result.endOfPaginationReached,
                        )
                    }
                }
            }
        }
    }

    private fun PagingState<Key, Item>.firstItemAtPositionOrNull(position: Int): Item? {
        val firstIndex = pages.firstOrNull()?.itemsBefore
            ?.takeIf { it != PagingSource.LoadResult.Page.COUNT_UNDEFINED }
            ?: 0
        if (position < firstIndex) return null
        return closestItemToPosition(position)
    }

    private fun PagingState<Key, Item>.lastItemAtPositionOrNull(position: Int): Item? {
        val lastIndex = pages.lastOrNull()?.itemsAfter
            ?.takeIf { it != PagingSource.LoadResult.Page.COUNT_UNDEFINED }
            ?: (pages.sumOf { it.data.size } - 1)
        if (position > lastIndex) return null
        return closestItemToPosition(position)
    }

    override suspend fun initialize() {
        initialize.invoke()
    }

    enum class ItemOrder {
        Ascending,
        Descending,
    }
}