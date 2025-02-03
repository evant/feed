package me.tatarka.android.feed.test

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingState
import me.tatarka.android.feed.FeedRemoteMediator

@ExperimentalPagingApi
class TestRemoteMediator : FeedRemoteMediator<Int, String> {

    var initialized: Boolean = false
    val loads = mutableListOf<FeedRemoteMediator.LoadDirection>()
    var refreshes = mutableListOf<PagingState<Int, String>>()

    var onLoad: (suspend (FeedRemoteMediator.LoadDirection) -> FeedRemoteMediator.LoadResult)? =
        null
    var onRefresh: (suspend () -> FeedRemoteMediator.RefreshResult)? = null

    override suspend fun load(
        direction: FeedRemoteMediator.LoadDirection,
        state: PagingState<Int, String>
    ): FeedRemoteMediator.LoadResult {
        loads.add(direction)
        return onLoad?.invoke(direction) ?: FeedRemoteMediator.LoadResult.Success(true)
    }

    override suspend fun refresh(state: PagingState<Int, String>): FeedRemoteMediator.RefreshResult {
        refreshes.add(state)
        return onRefresh?.invoke() ?: FeedRemoteMediator.RefreshResult.Success(
            endOfPrependReached = true,
            endOfAppendReached = true
        )
    }

    override suspend fun initialize() {
        initialized = true
    }
}