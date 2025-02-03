plugins {
    alias(libs.plugins.feed.android.library)
    alias(libs.plugins.feed.publish)
}

android {
    namespace = "me.tatarka.android.feed"
}

dependencies {
    api(libs.androidx.paging)
    testImplementation(project(":feed-test"))
    testImplementation(libs.androidx.paging.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
}