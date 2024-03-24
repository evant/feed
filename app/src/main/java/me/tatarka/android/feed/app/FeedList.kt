package me.tatarka.android.feed.app

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.tatarka.android.feed.FeedData
import me.tatarka.android.feed.FeedEntry
import me.tatarka.android.feed.FeedListHelper

@Stable
class FeedLazyList<T>(
    private val scope: CoroutineScope,
    private val onUpdateScrollPosition: FeedLazyList<T>.(LazyListLayoutInfo) -> Unit = {},
) : AbstractList<FeedEntry<T>>() {

    var listState: LazyListState? by mutableStateOf(null)
        private set

    private val helper = FeedListHelper()

    private val currentList = SnapshotStateList<FeedEntry<T>>()

    override val size: Int get() = currentList.size

    override fun get(index: Int): FeedEntry<T> {
        return currentList[index]
    }

    fun update(data: FeedData<T>) {
        helper.submitData(data) { type, items ->
            when (type) {
                is FeedData.Type.Initial -> {
                    if (listState == null) {
                        listState =
                            LazyListState(firstVisibleItemIndex = type.firstVisiblePosition).also {
                                watchListState(it)
                            }
                    }
                    currentList.clear()
                    currentList.addAll(items)
                }

                FeedData.Type.Prepend -> {
                    currentList.addAll(0, items)
                }

                FeedData.Type.Append -> {
                    currentList.addAll(items)
                }
            }

        }
    }

    suspend fun refresh() {
        helper.refresh()
    }

    private fun watchListState(state: LazyListState) {
        scope.launch {
            snapshotFlow { state.layoutInfo }.collect { layoutInfo ->
                val firstVisibleIndex =
                    layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return@collect
                val lastVisibleIndex =
                    layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                helper.updateVisibleWindow(firstVisibleIndex, lastVisibleIndex - firstVisibleIndex)
                onUpdateScrollPosition(layoutInfo)
            }
        }
    }
}

fun <T> feedLazyListOf(vararg items: T): FeedLazyList<T> {
    return FeedLazyList<T>(scope = CoroutineScope(Dispatchers.Unconfined)).apply {
        update(
            FeedData(
                type = FeedData.Type.Initial(firstVisiblePosition = 0),
                items = items.toList(),
                callbacks = object : FeedData.Callbacks {
                    override fun updateVisibleWindow(
                        firstVisiblePosition: Int,
                        visibleItemCount: Int
                    ) {
                    }

                    override suspend fun refresh() {
                    }
                },
            )
        )
    }
}

object FeedListScrollPositions {
    fun <T> firstVisibleIndex(onUpdate: (T) -> Unit): FeedLazyList<T>.(LazyListLayoutInfo) -> Unit =
        { layoutInfo ->
            val index = layoutInfo.visibleItemsInfo.firstOrNull()?.index
            if (index != null) {
                onUpdate(get(index).item)
            }
        }
}

@Composable
fun <T> Flow<FeedData<T>>.collectAsLazyList(
    onUpdateScrollPosition: FeedLazyList<T>.(LazyListLayoutInfo) -> Unit = {}
): FeedLazyList<T> {
    val scope = rememberCoroutineScope()
    val list = remember { FeedLazyList<T>(scope, onUpdateScrollPosition) }
    LaunchedEffect(this) {
        collect { data ->
            list.update(data)
        }
    }
    return list
}