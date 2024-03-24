package me.tatarka.android.feed.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ItemEntity(
    @PrimaryKey val id: Long,
    val text: String,
    @ColumnInfo(defaultValue = "false")
    val enabled: Boolean = false,
)