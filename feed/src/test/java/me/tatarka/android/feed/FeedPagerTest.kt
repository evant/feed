package me.tatarka.android.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.PagingConfig
import androidx.paging.testing.asPagingSourceFactory
import androidx.paging.testing.asSnapshot
import assertk.assertThat
import assertk.assertions.first
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class FeedPagerTest {

    @Test
    fun loads_from_initial_position() = runTest {
        val pagingSourceFactory = flowOf(List(20) { it.toLong() })
            .asPagingSourceFactory(backgroundScope)

        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            loadFrom = {
                InitialLoadState(initialKey = 5, itemKey = 10L)
            },
            pagingSourceFactory = pagingSourceFactory,
        )

        val snapshot = pager.flow.asSnapshot()

        assertThat(snapshot).first().isEqualTo(5L)
    }

    @Test
    fun full_refresh_resets_paging_source() = runTest {
        val pagingSourceFactory = InvalidatingPagingSourceFactory(
            flowOf(List(20) { it.toLong() })
                .asPagingSourceFactory(backgroundScope)
        )
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            pagingSourceFactory = pagingSourceFactory,
        )

        val snapshot = pager.flow.asSnapshot {
            scrollTo(19)
            (pager.connection as FeedConnection.FullRefresh)
                .fullRefresh(pagingSourceFactory::invalidate)
        }

        assertThat(snapshot).first().isEqualTo(0)
    }

    @Test
    fun full_refresh_ignores_initial_position() = runTest {
        val pagingSourceFactory = InvalidatingPagingSourceFactory(
            flowOf(List(20) { it.toLong() })
                .asPagingSourceFactory(backgroundScope)
        )
        val pager = FeedPager(
            config = PagingConfig(pageSize = 1),
            loadFrom = {
                InitialLoadState(initialKey = 5, itemKey = 10L)
            },
            pagingSourceFactory = pagingSourceFactory,
        )

        val snapshot = pager.flow.asSnapshot {
            scrollTo(19)
            (pager.connection as FeedConnection.FullRefresh)
                .fullRefresh(pagingSourceFactory::invalidate)
        }

        assertThat(snapshot).first().isEqualTo(0)
    }
}