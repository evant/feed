package me.tatarka.android.feed.app.api

import android.util.Log
import kotlinx.coroutines.delay

class ItemApi {

    private val apiItems = List(200) { i ->
        ApiItem(id = i + 1L, "Item $i")
    }

    suspend fun get(
        offset: Long = 0,
        limit: Int = 20
    ): List<ApiItem> {
        Log.d("api", "fetch: offset=${offset}, limit=${limit}")
        delay(500)
        return apiItems.asSequence()
            .drop(offset.toInt())
            .take(limit)
            .toList()
    }

}

class ApiItem(val id: Long, val text: String)