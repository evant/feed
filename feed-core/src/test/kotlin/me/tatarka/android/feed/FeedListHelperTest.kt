package me.tatarka.android.feed

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import org.junit.Test

class FeedListHelperTest {

    @Test
    fun updates_backing_list_from_submitted_data() {
        val list = BasicFeedList<Int>()
        list.submit(
            FeedData(
                type = FeedData.Type.Initial(firstVisiblePosition = 0),
                items = listOf(3, 4, 5),
                callbacks = TestCallbacks()
            )
        )

        assertThat(list).extracting { it.item }.containsExactly(3, 4, 5)

        list.submit(
            FeedData(
                type = FeedData.Type.Prepend,
                items = listOf(0, 1, 2),
                callbacks = TestCallbacks()
            )
        )

        assertThat(list).extracting { it.item }.containsExactly(0, 1, 2, 3, 4, 5)

        list.submit(
            FeedData(
                type = FeedData.Type.Append,
                items = listOf(6, 7, 8),
                callbacks = TestCallbacks()
            )
        )

        assertThat(list).extracting { it.item }.containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8)
    }
}

class TestCallbacks : FeedData.Callbacks {
    override fun updateVisibleWindow(firstVisiblePosition: Int, visibleItemCount: Int) {
    }

    override suspend fun refresh() {
    }
}

class BasicFeedList<T> : AbstractList<FeedEntry<T>>() {
    private val backingList = mutableListOf<FeedEntry<T>>()
    private val helper = FeedListHelper()

    override val size: Int get() = backingList.size

    override fun get(index: Int): FeedEntry<T> {
        return backingList[index]
    }

    fun submit(data: FeedData<T>) {
        helper.submitData(data) { type, items ->
            when (type) {
                is FeedData.Type.Initial -> {
                    backingList.clear()
                    backingList.addAll(items)
                }

                FeedData.Type.Append -> {
                    backingList.addAll(items)
                }

                FeedData.Type.Prepend -> {
                    backingList.addAll(0, items)
                }
            }
        }
    }
}