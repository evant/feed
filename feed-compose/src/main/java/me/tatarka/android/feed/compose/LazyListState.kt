package me.tatarka.android.feed.compose

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun <T : Any> LazyPagingItems<T>.rememberLazyListState(
    initialFirstVisibleItemIndex: ((itemKey: Any?) -> Int)? = null,
): State<LazyListState> {
    val loadKey = this.loadKey
    val listState = key(loadKey) {
        if (loadKey != null) {
            rememberSaveable(saver = LazyListState.Saver) {
                val offset = initialFirstVisibleItemIndex?.invoke(
                    initialItemKey
                )?.coerceIn(0, itemCount) ?: 0
                LazyListState(firstVisibleItemIndex = offset)
            }
        } else {
            LazyListState()
        }
    }
    return rememberUpdatedState(listState)
}

fun LazyListLayoutInfo.firstVisibleItemIndex(): Int {
    var index = -1
    // We want to find the 'most' visible item to scroll back to. There are 2 cases:
    // 1. one or more items is fully visible on the screen, pick the first one
    // 2. no items are fully visible, pick the first item that's partially visible
    for (item in visibleItemsInfo) {
        val itemFullyVisible = fullyVisible(item)
        if (itemFullyVisible) {
            return item.index
        }
        val itemPartiallyVisible = partiallyVisible(item)
        if (itemPartiallyVisible && index < 0) {
            index = item.index
        }
    }
    return index.coerceAtLeast(0)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun LazyListLayoutInfo.partiallyVisible(item: LazyListItemInfo): Boolean {
    return (item.offset + item.size) > 0 &&
            item.offset < viewportSize.height - beforeContentPadding - afterContentPadding
}

@Suppress("NOTHING_TO_INLINE")
private inline fun LazyListLayoutInfo.fullyVisible(item: LazyListItemInfo): Boolean {
    return item.offset >= 0 &&
            (item.offset + item.size) <= viewportSize.height - beforeContentPadding - afterContentPadding
}
