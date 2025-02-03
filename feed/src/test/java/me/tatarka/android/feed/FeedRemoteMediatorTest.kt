package me.tatarka.android.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.testing.asPagingSourceFactory
import androidx.paging.testing.asSnapshot
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.single
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.tatarka.android.feed.test.TestRemoteMediator
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediatorTest {

    @Test
    fun triggers_prepend_append_on_initial_load() = runTest {
        val mediator = TestRemoteMediator()
        val pagingSourceFactory = flowOf(emptyList<String>())
            .asPagingSourceFactory(backgroundScope)
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = mediator,
            pagingSourceFactory = pagingSourceFactory,
        )
        pager.flow.asSnapshot()

        assertThat(mediator.initialized).isTrue()
        assertThat(mediator.loads).containsExactly(
            FeedRemoteMediator.LoadDirection.Prepend,
            FeedRemoteMediator.LoadDirection.Append
        )
    }

    @Test
    fun refresh_can_stop_prepend() = runTest {
        val mediator = TestRemoteMediator()
        val localItems = MutableSharedFlow<List<String>>(replay = 1)
        localItems.emit(emptyList())
        mediator.onRefresh = {
            localItems.emit(emptyList())
            FeedRemoteMediator.RefreshResult.Success(
                endOfPrependReached = true,
                endOfAppendReached = false,
            )
        }
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )
        pager.flow.asSnapshot {
            mediator.loads.clear()
            refresh()
        }

        assertThat(mediator.refreshes).hasSize(1)
        assertThat(mediator.loads).containsExactly(
            FeedRemoteMediator.LoadDirection.Append,
        )
    }

    @Test
    fun refresh_can_stop_append() = runTest {
        val mediator = TestRemoteMediator()
        val localItems = MutableSharedFlow<List<String>>(replay = 1)
        localItems.emit(emptyList())
        mediator.onRefresh = {
            localItems.emit(emptyList())
            FeedRemoteMediator.RefreshResult.Success(
                endOfPrependReached = false,
                endOfAppendReached = true,
            )
        }
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )
        pager.flow.asSnapshot {
            mediator.loads.clear()
            refresh()
        }

        assertThat(mediator.refreshes).hasSize(1)
        assertThat(mediator.loads).containsExactly(
            FeedRemoteMediator.LoadDirection.Prepend,
        )
    }

    @Test
    fun refresh_includes_anchor() = runTest {
        val mediator = TestRemoteMediator()
        val localItems = MutableSharedFlow<List<String>>(replay = 1)
        localItems.emit(List(50) { "$it" })
        mediator.onRefresh = {
            localItems.emit(emptyList())
            FeedRemoteMediator.RefreshResult.Success(
                endOfPrependReached = true,
                endOfAppendReached = true,
            )
        }
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )
        pager.flow.asSnapshot {
            mediator.loads.clear()
            refresh()
        }

        assertThat(mediator.refreshes)
            .single()
            .prop(PagingState<*, *>::anchorPosition)
            .isNotNull()
    }

    @Test
    fun full_refresh_does_not_include_anchor() = runTest {
        val mediator = TestRemoteMediator()
        val localItems = MutableSharedFlow<List<String>>(replay = 1)
        localItems.emit(List(50) { "$it" })
        mediator.onRefresh = {
            localItems.emit(emptyList())
            FeedRemoteMediator.RefreshResult.Success(
                endOfPrependReached = true,
                endOfAppendReached = true,
            )
        }
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )
        pager.flow.asSnapshot {
            mediator.loads.clear()
            val doRefresh = launch(start = CoroutineStart.LAZY) { refresh() }
            (pager.connection as FeedConnection.FullRefresh).fullRefresh { doRefresh.start() }
        }

        assertThat(mediator.refreshes)
            .single()
            .prop(PagingState<*, *>::anchorPosition)
            .isNull()
    }
}