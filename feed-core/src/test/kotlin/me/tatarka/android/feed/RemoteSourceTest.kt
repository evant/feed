@file:OptIn(ExperimentalCoroutinesApi::class)

package me.tatarka.android.feed

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class RemoteSourceTest {
    @Test
    fun fetches_data_from_remote_mediator_when_source_is_empty() = runTest {
        val dataSource = InMemoryDataStore<Int>()
        val remoteSource = InMemoryRemoteSource(dataSource)
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3),
            localSource = dataSource.asFeedSource(),
            remoteSource = remoteSource,
        )
        remoteSource.pushPageAfter(listOf(1, 2, 3))
        feed.test {
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(1, 2, 3)
        }
    }

    @Test
    fun fetches_next_page_when_end_of_data_reached() = runTest {
        val dataSource = InMemoryDataStore<Int>()
        dataSource.insertAfter(listOf(1, 2, 3))
        val remoteSource = InMemoryRemoteSource(dataSource)
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3, prefetchDistance = 0),
            localSource = dataSource.asFeedSource(),
            remoteSource = remoteSource,
        )
        remoteSource.pushPageAfter(listOf(4, 5, 6))
        feed.test {
            val data = awaitItem()
            assertThat(data).prop(FeedData<Int>::items)
                .containsExactly(1, 2, 3)
            data.callbacks.updateVisibleWindow(3, 3)
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(3, 4, 5, 6)
        }
    }

    @Test
    fun refresh_resets_content_and_refetches_around_the_current_window() = runTest {
        val dataSource = InMemoryDataStore<Int>()
        dataSource.insertAfter(listOf(1, 2, 3, 4, 5, 6))
        val remoteSource = InMemoryRemoteSource(dataSource)
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3, prefetchDistance = 0),
            localSource = dataSource.asFeedSource(),
            remoteSource = remoteSource,
        )
        feed.test {
            var data = awaitItem() // 1, 2, 3
            data.callbacks.updateVisibleWindow(3, 3)
            data = awaitItem() // 4, 5, 6

            remoteSource.setRefreshContent(listOf(4, 5, 6))
            data.callbacks.refresh()
            remoteSource.pushPageBefore(listOf(1, 2, 3), lastPage = true)

            data = awaitItem()
            assertThat(data).prop(FeedData<Int>::items)
                .containsExactly(4, 5, 6)
            data.callbacks.updateVisibleWindow(0, 3)
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(1, 2, 3, 4)
        }
    }

    @Test
    fun refresh_cancels_and_restarts_load_after() = runTest {
        val dataSource = InMemoryDataStore<Int>()
        dataSource.insertAfter(listOf(1, 2, 3))
        val remoteSource = InMemoryRemoteSource(dataSource)
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3, prefetchDistance = 0),
            localSource = dataSource.asFeedSource(),
            remoteSource = remoteSource,
        )
        remoteSource.setRefreshContent(listOf(1, 2, 3))

        feed.test {
            val data = awaitItem() // 1, 2, 3
            data.callbacks.refresh()

            assertThat(remoteSource.awaitOnLoadAfter()).isEqualTo(RemoteSource.LoadState.Initial)
            assertThat(remoteSource.awaitOnLoadAfterCanceled()).isEqualTo(RemoteSource.LoadState.Initial)
            assertThat(remoteSource.awaitOnLoadAfter()).isEqualTo(RemoteSource.LoadState.Refresh)
        }
    }

    @Test
    fun loads_from_the_correct_position_after_a_refresh() = runTest {
        val dataSource = InMemoryDataStore<Int>()
        dataSource.insertAfter(listOf(1, 2, 3, 4, 5, 6))
        val remoteSource = InMemoryRemoteSource(dataSource)
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3, prefetchDistance = 0),
            localSource = dataSource.asFeedSource(),
            remoteSource = remoteSource,
        )

        remoteSource.setRefreshContent(listOf(4, 5, 6))

        feed.test {
            val data = awaitItem() // 4, 5, 6
            data.callbacks.refresh()

            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(4, 5, 6)
        }
    }
}