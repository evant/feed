package me.tatarka.android.feed.app.api

import kotlinx.coroutines.delay
import me.tatarka.android.feed.app.Item

class ItemApi(itemCount: Int = 200) {

    private val apiItems = List(itemCount) { i ->
        val id = i + 1L
        ApiItem(id = id, "Item $id")
    }

    suspend fun get(
        minId: Long? = null,
        maxId: Long? = null,
        size: Int = 20,
    ): List<ApiItem> {
        delay(1000)
        val minIndex = minId?.let { m -> apiItems.indexOfFirst { it.id == m } }?.takeIf { it >= 0 }
        val maxIndex = maxId?.let { m -> apiItems.indexOfLast { it.id == m } }?.takeIf { it >= 0 }
        return if (minIndex == null && maxIndex == null) {
            apiItems.take(size)
        } else if (minIndex != null && maxIndex != null) {
            apiItems.subList(minIndex + 1, maxIndex.coerceAtMost(apiItems.size))
        } else if (minIndex != null) {
            apiItems.subList(minIndex + 1, (minIndex + 1 + size).coerceAtMost(apiItems.size))
        } else if (maxIndex != null) {
            apiItems.subList((maxIndex - size).coerceAtLeast(0), maxIndex)
        } else {
            throw IllegalStateException()
        }
    }

}

data class ApiItem(val id: Long, val text: String) : Comparable<ApiItem> {
    override fun compareTo(other: ApiItem): Int = id.compareTo(other.id)
}

fun ApiItem.toItem(): Item = Item(
    id = id,
    name = text
)