package me.tatarka.android.feed.app.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract val itemDao: ItemDao

    companion object {
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: Room
            .databaseBuilder(context, AppDatabase::class.java, "db")
            .setQueryCallback({ sqlQuery, bindArgs ->
                Log.e("query", "$sqlQuery [${bindArgs.joinToString()}]")
            }, executor = Runnable::run)
            .build().also { instance = it }
    }
}
