package me.tatarka.android.feed

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.prop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FeedTest {

    @Test
    fun loads_initial_page_around_default_scroll_position() = runTest {
        val feed = feed(
            localSource = flowOf(localSourceOf(1, 2, 3)),
            remoteSource = EmptyRemoteSource(),
        )
        feed.test {
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(1, 2, 3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loads_initial_page_around_custom_scroll_position() = runTest {
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3),
            localSource = flowOf(localSourceOf(1, 2, 3, 4)),
            remoteSource = EmptyRemoteSource(),
            initialOffset = { 2 },
        )
        feed.test {
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(2, 3, 4)
        }
    }

    @Test
    fun loads_next_page() = runTest {
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3),
            localSource = flowOf(localSourceOf(1, 2, 3, 4, 5)),
            remoteSource = EmptyRemoteSource(),
        )
        feed.test {
            val data = awaitItem()
            assertThat(data).prop(FeedData<Int>::items)
                .containsExactly(1, 2, 3)
            data.callbacks.updateVisibleWindow(3, 3)
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(4, 5)
        }
    }

    @Test
    fun loads_previous_page() = runTest {
        val feed = feed(
            options = FeedOptions(pageSize = 3, initialLoadSize = 3, prefetchDistance = 0),
            localSource = flowOf(localSourceOf(1, 2, 3, 4)),
            remoteSource = EmptyRemoteSource(),
            initialOffset = { 3 },
        )
        feed.test {
            val data = awaitItem()
            assertThat(data).prop(FeedData<Int>::items)
                .containsExactly(3, 4)
            data.callbacks.updateVisibleWindow(0, 3)
            assertThat(awaitItem()).prop(FeedData<Int>::items)
                .containsExactly(1, 2)
        }
    }
}