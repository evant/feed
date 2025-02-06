package me.tatarka.android.feed

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import me.tatarka.android.feed.FeedRemoteMediator.LoadDirection
import me.tatarka.android.feed.FeedRemoteMediator.LoadResult
import me.tatarka.android.feed.FeedRemoteMediator.RefreshResult
import kotlin.coroutines.resume

@ExperimentalPagingApi
internal class FeedRemoteMediatorAdapter<Key : Any, Value : Any>(
    private val remoteMediator: FeedRemoteMediator<Key, Value>
) : RemoteMediator<Key, Value>() {

    private var firstLoad = FirstLoadState.First
    private var endOfPrependReached = false
    private var endOfAppendReached = false
    private var refreshing: CancellableContinuation<Unit>? = null

    override suspend fun load(loadType: LoadType, state: PagingState<Key, Value>): MediatorResult {
        if (firstLoad == FirstLoadState.First && state.isEmpty()) {
            firstLoad = FirstLoadState.Skip
            // If we are starting from an empty state then both a prepend & append will be
            // fired. We can simplify implementations by calling refresh instead.
            // This allows it to report which direction future loading may take place,
            // possibly short-circuiting future calls.
            return when (val result = remoteMediator.refresh(state)) {
                is RefreshResult.Error -> MediatorResult.Error(result.throwable)
                is RefreshResult.Success -> {
                    endOfPrependReached = result.endOfPrependReached
                    endOfAppendReached = result.endOfAppendReached
                    MediatorResult.Success(endOfPaginationReached = endOfPrependReached && endOfAppendReached)
                }
            }
        } else if (firstLoad == FirstLoadState.Skip) {
            firstLoad = FirstLoadState.None
            return MediatorResult.Success(endOfPaginationReached = endOfAppendReached)
        } else {
            firstLoad = FirstLoadState.None
        }

        return when (loadType) {
            LoadType.REFRESH -> {
                val refreshState = if (refreshing != null) {
                    PagingState(
                        pages = emptyList(),
                        anchorPosition = null,
                        config = state.config,
                        leadingPlaceholderCount = 0,
                    )
                } else {
                    state
                }
                when (val result = remoteMediator.refresh(refreshState)) {
                    is RefreshResult.Success -> {
                        endOfPrependReached = result.endOfPrependReached
                        endOfAppendReached = result.endOfAppendReached
                        refreshing?.resume(Unit)
                        refreshing = null
                        MediatorResult.Success(endOfPaginationReached = endOfPrependReached && endOfAppendReached)
                    }

                    is RefreshResult.Error -> {
                        MediatorResult.Error(result.throwable)
                    }
                }
            }

            LoadType.PREPEND -> {
                if (endOfPrependReached) {
                    MediatorResult.Success(endOfPaginationReached = true)
                } else {
                    when (val result = remoteMediator.load(LoadDirection.Prepend, state)) {
                        is LoadResult.Success -> {
                            MediatorResult.Success(endOfPaginationReached = result.endOfPaginationReached)
                        }

                        is LoadResult.Error -> MediatorResult.Error(result.throwable)
                    }
                }
            }

            LoadType.APPEND -> {
                if (endOfAppendReached) {
                    MediatorResult.Success(endOfPaginationReached = true)
                } else {
                    when (val result = remoteMediator.load(LoadDirection.Append, state)) {
                        is LoadResult.Success -> {
                            MediatorResult.Success(endOfPaginationReached = result.endOfPaginationReached)
                        }

                        is LoadResult.Error -> MediatorResult.Error(result.throwable)
                    }
                }
            }
        }
    }

    suspend fun fullRefresh(refreshAction: () -> Unit) {
        suspendCancellableCoroutine { continuation ->
            refreshing = continuation
            continuation.invokeOnCancellation {
                refreshing = null
            }
            refreshAction()
        }
    }

    override suspend fun initialize(): InitializeAction {
        remoteMediator.initialize()
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    private enum class FirstLoadState {
        First, Skip, None
    }
}