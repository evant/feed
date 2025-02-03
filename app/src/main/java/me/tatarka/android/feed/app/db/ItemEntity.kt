package me.tatarka.android.feed.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.tatarka.android.feed.app.Item

@Entity
data class ItemEntity(
    @PrimaryKey val id: Long,
    val text: String,
    @ColumnInfo(defaultValue = "false")
    val enabled: Boolean = false,
)

fun ItemEntity.toItem(onCheckedChange: (Boolean) -> Unit): Item = Item(
    id = id,
    name = text,
    checked = enabled,
    onCheckedChange = onCheckedChange,
)