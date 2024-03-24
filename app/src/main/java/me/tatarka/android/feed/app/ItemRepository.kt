package me.tatarka.android.feed.app

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.android.feed.FeedOptions
import me.tatarka.android.feed.LocalSource
import me.tatarka.android.feed.RemoteSource
import me.tatarka.android.feed.app.api.ItemApi
import me.tatarka.android.feed.app.db.AppDatabase
import me.tatarka.android.feed.app.db.ItemDao
import me.tatarka.android.feed.app.db.ItemEntity
import me.tatarka.android.feed.feed
import me.tatarka.android.feed.mapItems
import me.tatarka.android.feed.onEachLoadAfter
import me.tatarka.android.feed.onEachLoadBefore

class ItemRepository(
    api: ItemApi,
    db: AppDatabase,
    scope: CoroutineScope,
    prefs: () -> SharedPreferences,
) {
    private val scrollPositionStore = SharedPrefsScrollPosition(
        dao = db.itemDao,
        prefs = lazy { prefs() },
        key = "scrollPosition"
    )

    val items = feed(
        options = FeedOptions(pageSize = 20),
        localSource = localItemSource(db),
        remoteSource = remoteItemSource(api, db.itemDao),
        initialOffset = { scrollPositionStore.initialOffset() }
    ).mapItems { entity ->
        Item(
            id = entity.id,
            name = entity.text,
            checked = entity.enabled,
            onCheckedChange = {
                scope.launch { db.itemDao.update(entity.id, it) }
            },
        )
    }

    fun updateScrollPosition(id: Long) {
        scrollPositionStore.saveScrollPosition(id)
    }
}

private fun localItemSource(db: AppDatabase): Flow<LocalSource<ItemEntity>> =
    observeTable(db, "ItemEntity").map {
        LocalSource { position, count ->
            db.itemDao.items(limit = count, offset = position)
        }
    }

private fun observeTable(db: RoomDatabase, tableName: String): Flow<Unit> = callbackFlow {
    send(Unit)
    val observer = object : InvalidationTracker.Observer(arrayOf(tableName)) {
        override fun onInvalidated(tables: Set<String>) {
            trySend(Unit)
        }
    }
    db.invalidationTracker.addObserver(observer)
    awaitClose {
        db.invalidationTracker.removeObserver(observer)
    }
}

private fun remoteItemSource(api: ItemApi, dao: ItemDao): RemoteSource =
    RemoteSource { _, pageSize ->
        onEachLoadBefore {
            val firstItemId = dao.firstItemId()
            val offset = (firstItemId - pageSize).coerceAtLeast(0)
            val count = (firstItemId - offset).toInt()
            if (count > 0) {
                val items = api.get(offset = offset, limit = count)
                dao.insertAll(items.map { ItemEntity(id = it.id, text = it.text) })
            }
            if (offset == 0L) complete()
        }

        onEachLoadAfter {
            val lastItemId = dao.lastItemId()
            val items = api.get(offset = lastItemId, limit = pageSize)
            dao.insertAll(items.map { ItemEntity(id = it.id, text = it.text) })
            if (items.isEmpty()) complete()
        }

        onEachRefresh { loadWindow ->
            val startOffset = dao.offsetOf(loadWindow.start)
            val items = api.get(offset = startOffset, limit = loadWindow.size)
            dao.insertAll(items.map { ItemEntity(id = it.id, text = it.text) }, refresh = true)
            0
        }
    }


private class SharedPrefsScrollPosition(
    private val dao: ItemDao,
    private val prefs: Lazy<SharedPreferences>,
    private val key: String,
) {
    suspend fun initialOffset(): Long {
        val itemId = prefs.value.getLong(key, -1)
        // default to the start of the list
        if (itemId == -1L) return 0
        return dao.offsetOf(itemId).also {
            Log.d("prefs", "load position:$it id:$itemId")
        }
    }

    fun saveScrollPosition(id: Long) {
        prefs.value.edit {
            putLong(key, id).also {
                Log.d("prefs", "save id:$id")
            }
        }
    }
}

class Item(
    val id: Long,
    val name: String,
    val checked: Boolean = false,
    val onCheckedChange: (Boolean) -> Unit = {},
)
