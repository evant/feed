package me.tatarka.android.feed.app.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ItemDao {
    @Query("SELECT * FROM ItemEntity ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun items(limit: Int, offset: Long): List<ItemEntity>

    @Query("SELECT * FROM ItemEntity ORDER BY id ASC")
    fun itemsPaging(): PagingSource<Int, ItemEntity>

    @Query("SELECT * FROM ItemEntity ORDER BY id DESC")
    fun itemsPagingReversed(): PagingSource<Int, ItemEntity>

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("DELETE FROM ItemEntity")
    suspend fun deleteAll()

    @Query("UPDATE ItemEntity SET enabled=:enabled WHERE id=:id")
    suspend fun update(id: Long, enabled: Boolean)

    @Transaction
    suspend fun insertAll(items: List<ItemEntity>, replace: Boolean) {
        if (replace) {
            deleteAll()
        }
        insertAll(items)
    }

    @Query("SELECT count(*) FROM ItemEntity WHERE id<:id")
    suspend fun offsetOf(id: Long): Int

    @Query("SELECT id FROM ItemEntity ORDER BY id ASC LIMIT 1")
    suspend fun firstItemId(): Long

    @Query("SELECT id FROM ItemEntity ORDER BY id DESC LIMIT 1")
    suspend fun lastItemId(): Long
}