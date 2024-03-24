package me.tatarka.android.feed

interface ScrollPositionStore {
    suspend fun load(refresh: Boolean): Long

    suspend fun save(position: Long)
}

class FixedScrollPositionStore(private val position: Long = 0) : ScrollPositionStore {

    override suspend fun load(refresh: Boolean): Long {
        return position
    }

    override suspend fun save(position: Long) {
        // ignore
    }
}