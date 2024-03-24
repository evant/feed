package me.tatarka.android.feed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FeedData<T>(
    val type: Type,
    val items: List<T>,
    val callbacks: Callbacks,
) {

    fun <R> map(transform: (T) -> R): FeedData<R> {
        return FeedData(
            type = type,
            items = items.map(transform),
            callbacks = callbacks,
        )
    }

    sealed interface Type {
        class Initial(val firstVisiblePosition: Int) : Type
        data object Prepend : Type
        data object Append : Type
    }

    interface Callbacks {
        fun updateVisibleWindow(firstVisiblePosition: Int, visibleItemCount: Int)

        suspend fun refresh()
    }
}

fun <T, R> Flow<FeedData<T>>.mapItems(transform: (T) -> R): Flow<FeedData<R>> =
    map { it.map(transform) }