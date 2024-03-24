package me.tatarka.android.feed

class LoadWindow(
    val start: Long,
    val size: Int,
) {
    init {
        require(size >= 0) { "size must not be negative $size" }
    }

    val end: Long get() = start + size

    companion object {
        val Empty = LoadWindow(0, 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoadWindow) return false
        if (start != other.start) return false
        if (size != other.size) return false
        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + size
        return result
    }

    override fun toString(): String {
        return "LoadWindow(start=$start, size=$size)"
    }
}

fun LoadWindow(
    startingOffset: Long,
    firstVisiblePosition: Int,
    visibleItemCount: Int,
    prefetchDistance: Int,
): LoadWindow {
    val start = startingOffset + firstVisiblePosition - prefetchDistance
    val prefetchStart = (prefetchDistance + start.coerceAtLeast(0) - start).toInt()
    return LoadWindow(
        start = start.coerceAtLeast(0),
        size = visibleItemCount + prefetchStart + prefetchDistance
    )
}