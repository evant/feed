package me.tatarka.android.feed.app

sealed interface LoadingState {
    data object Loading : LoadingState
    data object NotLoading : LoadingState
    class Error(val error: Throwable): LoadingState
}
