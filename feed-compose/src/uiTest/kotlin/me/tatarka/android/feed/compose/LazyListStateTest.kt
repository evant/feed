package me.tatarka.android.feed.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.testing.asPagingSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isZero
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.tatarka.android.feed.FeedPager
import me.tatarka.android.feed.InitialLoadState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class LazyListStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loads_content() {
        val pagingSourceFactory = flowOf(List(10) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = pagingSourceFactory,
        )

        lateinit var listState: LazyListState
        composeTestRule.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState().value
            TestList(
                items = items,
                listState = listState,
            )
        }
        composeTestRule.waitForIdle()

        assertThat(listState.layoutInfo.visibleItemsInfo).isNotEmpty()
    }

    @Test
    fun loads_to_initial_offset() {
        val pagingSourceFactory = flowOf(List(30) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 10),
            loadFrom = { InitialLoadState(initialKey = 0, itemKey = 5L) },
            pagingSourceFactory = pagingSourceFactory,
        )

        lateinit var listState: LazyListState

        composeTestRule.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState(
                initialFirstVisibleItemIndex = { items.itemSnapshotList.indexOf(it) }
            ).value
            TestList(
                items = items,
                listState = listState,
            )
        }
        composeTestRule.waitForIdle()

        assertThat(listState.firstVisibleItemIndex).isEqualTo(5)
    }

    @Test
    fun full_refresh_moves_to_top_after_load() {
        val pagingSourceFactory = flowOf(List(40) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 15),
            pagingSourceFactory = pagingSourceFactory,
        )

        val refresh = MutableSharedFlow<Unit>()
        lateinit var listState: LazyListState

        composeTestRule.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState().value
            TestList(
                items = items,
                listState = listState,
                refresh = refresh,
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasTestTag("list"))
            .performScrollToNode(hasText("39"))
        runBlocking { refresh.emit(Unit) }
        composeTestRule.waitForIdle()

        assertThat(listState.firstVisibleItemIndex).isZero()
    }

    @Test
    fun full_refresh_after_later_page_moves_to_top() {
        val pagingSourceFactory = flowOf(List(40) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = pagingSourceFactory,
        )

        val refresh = MutableSharedFlow<Unit>()
        lateinit var listState: LazyListState

        composeTestRule.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState().value
            TestList(
                items = items,
                listState = listState,
                refresh = refresh,
            )
        }
        composeTestRule.onNode(hasTestTag("list"))
            .performScrollToNode(hasText("39"))
        runBlocking { refresh.emit(Unit) }
        composeTestRule.waitForIdle()

        assertThat(listState.firstVisibleItemIndex).isZero()
    }

    @Test
    fun preserves_scroll_position_on_configuration_change() {
        val pagingSourceFactory = flowOf(List(40) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = pagingSourceFactory,
        )

        lateinit var listState: LazyListState

        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState().value
            TestList(
                items = items,
                listState = listState,
            )
        }
        composeTestRule.onNode(hasTestTag("list"))
            .performScrollToIndex(10)

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(listState.firstVisibleItemIndex).isEqualTo(10)
    }

    @Test
    fun preserves_scroll_position_on_configuration_change_after_refresh() {
        val pagingSourceFactory = flowOf(List(40) { it.toLong() })
            .asPagingSourceFactory(CoroutineScope(Dispatchers.Unconfined))
        val pager = FeedPager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = pagingSourceFactory,
        )

        val refresh = MutableSharedFlow<Unit>()
        lateinit var listState: LazyListState

        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val items = pager.flow.collectAsLazyPagingItems(pager.connection)
            listState = items.rememberLazyListState().value
            TestList(
                items = items,
                listState = listState,
                refresh = refresh,
            )
        }
        runBlocking { refresh.emit(Unit) }
        composeTestRule.onNode(hasTestTag("list"))
            .performScrollToIndex(10)

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(listState.firstVisibleItemIndex).isEqualTo(10)
    }

    @Composable
    fun TestList(
        items: LazyPagingItems<Long>,
        listState: LazyListState,
        refresh: Flow<Unit>? = null,
    ) {
        LaunchedEffect(refresh) {
            refresh?.collectLatest {
                items.refresh()
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("list"),
            state = listState
        ) {
            items(count = items.itemCount, key = items.itemKey { it }) { index ->
                val item = items[index]
                BasicText(
                    text = item?.toString() ?: "",
                    style = TextStyle(fontSize = 24.sp),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
