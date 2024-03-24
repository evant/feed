package me.tatarka.android.feed

class FeedListHelper {

    private lateinit var callbacks: FeedData.Callbacks

    fun <T> submitData(data: FeedData<T>, updateList: (FeedData.Type, List<FeedEntry<T>>) -> Unit) {
        callbacks = data.callbacks

        when (val type = data.type) {
            is FeedData.Type.Initial -> {
                updateList(
                    type,
                    data.items.map { FeedEntry.Item(it) }
                )
            }

            FeedData.Type.Prepend -> {
                updateList(
                    type,
                    data.items.map { FeedEntry.Item(it) }
                )
            }

            FeedData.Type.Append -> {
                updateList(
                    type,
                    data.items.map { FeedEntry.Item(it) }
                )
            }
        }
    }

    fun updateVisibleWindow(firstVisibleIndex: Int, visibleItemCount: Int) {
        callbacks.updateVisibleWindow(
            firstVisibleIndex,
            visibleItemCount,
        )
    }

    suspend fun refresh() {
        callbacks.refresh()
    }
}

sealed interface FeedEntry<T> {
    class Item<T>(val current: T) : FeedEntry<T>
    class Delete<T>(val previous: T) : FeedEntry<T>
    class Update<T>(val previous: T, val current: T) : FeedEntry<T>

    val item: T
        get() = when (this) {
            is Item -> current
            is Delete -> previous
            is Update -> current
        }
}
