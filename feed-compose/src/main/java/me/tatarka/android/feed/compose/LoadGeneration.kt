package me.tatarka.android.feed.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal class LoadGeneration {

    var currentGeneration by mutableIntStateOf(0)
        private set

    private var loadGeneration by mutableIntStateOf(0)

    val loading: Boolean get() = currentGeneration != loadGeneration

    fun startLoading() {
        loadGeneration += 1
    }

    fun endLoading() {
        currentGeneration = loadGeneration
    }

    companion object {
        val Saver: Saver<LoadGeneration, Int> = Saver(
            save = {
                it.currentGeneration
            },
            restore = {
                LoadGeneration().apply {
                    currentGeneration = it
                    loadGeneration = it
                }
            }
        )
    }
}

@Composable
internal fun rememberLoadGeneration(): LoadGeneration {
    return rememberSaveable(saver = LoadGeneration.Saver) { LoadGeneration() }
}