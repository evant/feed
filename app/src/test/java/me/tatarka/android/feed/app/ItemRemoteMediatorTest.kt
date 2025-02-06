package me.tatarka.android.feed.app

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.testing.asPagingSourceFactory
import androidx.paging.testing.asSnapshot
import assertk.assertThat
import assertk.assertions.containsExactly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import me.tatarka.android.feed.FeedPager
import me.tatarka.android.feed.FeedRemoteMediator
import me.tatarka.android.feed.app.api.ApiItem
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.api.ItemRemoteMediator
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class ItemRemoteMediatorTest {

    val api = ItemApi(itemCount = 6)
    val localItems = MutableStateFlow(emptyList<ApiItem>())

    val requests = mutableListOf<FetchRequest>()
    val fetch = ItemRemoteMediator.Fetcher<ApiItem> { afterItem, beforeItem, size, replace ->
        requests.add(FetchRequest(afterItem?.id, beforeItem?.id, size, replace))
        val items = api.get(
            minId = afterItem?.id,
            maxId = beforeItem?.id,
            size = size
        )
        insert(items, replace)

        FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = items.size < size)
    }

    @Test
    fun initial_load_does_single_fetch_with_null_items() = runTest {
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetch)
        val config = PagingConfig(pageSize = 1)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot()

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = null,
                beforeId = null,
                size = config.initialLoadSize,
                replace = true,
            ),
            // append
            FetchRequest(
                afterId = 3L,
                beforeId = null,
                size = config.pageSize,
                replace = false,
            )
        )
    }

    @Test
    fun loads_prev_and_next_pages_asc() = runTest {
        // start with 3, 4, 5
        insert(List(3) { ApiItem(it.toLong() + 3, "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetcher = fetch)
        val config = PagingConfig(pageSize = 3)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot()

        assertThat(requests).containsExactly(
            FetchRequest(
                afterId = null,
                beforeId = 3,
                size = config.pageSize,
                replace = false
            ),
            FetchRequest(
                afterId = 5,
                beforeId = null,
                size = config.pageSize,
                replace = false
            ),
        )
    }

    @Test
    fun loads_prev_and_next_pages_dsc() = runTest {
        // start with 5, 4, 3
        insert(List(3) { ApiItem(5 - it.toLong(), "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(
            fetch,
            ItemRemoteMediator.ItemOrder.Descending,
        )
        val config = PagingConfig(pageSize = 3)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.map { it.reversed() }
                .asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot()

        assertThat(requests).containsExactly(
            FetchRequest(
                afterId = 5,
                beforeId = null,
                size = config.pageSize,
                replace = false
            ),
            FetchRequest(
                afterId = null,
                beforeId = 3,
                size = config.pageSize,
                replace = false
            ),
        )
    }

    @Test
    fun refresh_on_empty_loads_initial() = runTest {
        val fetch = ItemRemoteMediator.Fetcher<ApiItem> { afterItem, beforeItem, size, replace ->
            requests.add(FetchRequest(afterItem?.id, beforeItem?.id, size, replace))
            insert(emptyList(), replace)
            FeedRemoteMediator.LoadResult.Success(endOfPaginationReached = true)
        }
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetcher = fetch)
        val config = PagingConfig(pageSize = 1)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            FetchRequest(
                afterId = null,
                beforeId = null,
                size = config.initialLoadSize,
                replace = true,
            )
        )
    }

    @Test
    fun refresh_loads_over_first_page_asc() = runTest {
        // 1, 2, 3, 4, 5, 6
        insert(List(6) { ApiItem(it + 1L, "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetch)
        val config = PagingConfig(pageSize = 2)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = 1,
                beforeId = null,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend
            FetchRequest(
                afterId = null,
                beforeId = 2,
                size = config.pageSize,
                replace = false
            )
        )
    }

    @Test
    fun refresh_loads_over_first_page_dec() = runTest {
        // 6, 5, 4, 3, 2, 1
        insert(List(6) {
            val id = 6L - it
            ApiItem(id, "$id")
        })

        val mediator = ItemRemoteMediator<Int, ApiItem>(
            fetch,
            order = ItemRemoteMediator.ItemOrder.Descending
        )
        val config = PagingConfig(pageSize = 2)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems
                .map { it.reversed() }
                .asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = null,
                beforeId = 6,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend
            FetchRequest(
                afterId = 5,
                beforeId = null,
                size = config.pageSize,
                replace = false,
            )
        )
    }

    @Test
    fun refresh_loads_over_last_page_asc() = runTest {
        // 1, 2, 3, 4, 5, 6
        insert(List(6) { ApiItem(it + 1L, "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetch)
        val config = PagingConfig(pageSize = 3)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            scrollTo(5)
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = 2,
                beforeId = null,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend, since refresh requested items after
            FetchRequest(
                afterId = null,
                beforeId = 3,
                size = config.pageSize,
                replace = false,
            )
        )
    }

    @Test
    fun refresh_loads_over_last_page_dec() = runTest {
        // 6, 5, 4, 3, 2, 1
        insert(List(6) {
            val id = 6L - it
            ApiItem(id, "$id")
        })

        val mediator = ItemRemoteMediator<Int, ApiItem>(
            fetch,
            order = ItemRemoteMediator.ItemOrder.Descending
        )
        val config = PagingConfig(pageSize = 2)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems
                .map { it.reversed() }
                .asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            scrollTo(5)
            requests.clear()
            refresh()
        }
        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = null,
                beforeId = 4,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend, since refresh requested items before
            FetchRequest(
                afterId = 3,
                beforeId = null,
                size = config.pageSize,
                replace = false,
            ),
            FetchRequest(
                afterId = 5,
                beforeId = null,
                size = config.pageSize,
                replace = false,
            ),
        )
    }

    @Test
    fun refresh_loads_over_large_page_size_asc() = runTest {
        // 1, 2, 3, 4, 5, 6
        insert(List(6) { ApiItem(it + 1L, "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetch)
        val config = PagingConfig(pageSize = 3)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = 1,
                beforeId = null,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend
            FetchRequest(
                afterId = null,
                beforeId = 2,
                size = config.pageSize,
                replace = false
            )
        )
    }

    @Test
    fun refresh_loads_over_small_page_size_asc() = runTest {
        // 1, 2, 3, 4, 5, 6
        insert(List(6) { ApiItem(it + 1L, "$it") })
        val mediator = ItemRemoteMediator<Int, ApiItem>(fetch)
        val config = PagingConfig(pageSize = 1)
        val pager = FeedPager(
            config = config,
            remoteMediator = mediator,
            pagingSourceFactory = localItems.asPagingSourceFactory(backgroundScope)
        )

        pager.flow.asSnapshot {
            requests.clear()
            refresh()
        }

        assertThat(requests).containsExactly(
            // refresh
            FetchRequest(
                afterId = null,
                beforeId = 3,
                size = config.initialLoadSize,
                replace = true,
            ),
            // prepend
            FetchRequest(
                afterId = 2,
                beforeId = null,
                size = config.pageSize,
                replace = false
            ),
            FetchRequest(
                afterId = 3,
                beforeId = null,
                size = config.pageSize,
                replace = false
            ),
        )
    }

    private fun insert(items: List<ApiItem>, replace: Boolean = false) {
        localItems.update { (if (replace) items else it + items).sorted() }
    }

    data class FetchRequest(
        val afterId: Long?,
        val beforeId: Long?,
        val size: Int,
        val replace: Boolean,
    )
}