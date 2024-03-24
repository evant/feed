package me.tatarka.android.feed

fun interface LocalSource<T> {
    suspend fun load(position: Long, count: Int): List<T>
}

fun <T> localSourceOf(vararg items: T): LocalSource<T> = LocalSource { position, count ->
    items.asSequence()
        .drop(position.toInt())
        .take(count)
        .toList()
}