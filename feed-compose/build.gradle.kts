plugins {
    alias(libs.plugins.feed.android.library)
    alias(libs.plugins.feed.publish)
    alias(libs.plugins.compose)
}

android {
    namespace = "me.tatarka.android.feed.compose"

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        if (project.providers.gradleProperty("deviceTest").getOrElse("false") == "true") {
            getByName("androidTest") {
                kotlin.srcDir("src/uiTest/kotlin")
                resources.srcDir("src/uiTest/resources")
            }
        } else {
            getByName("test") {
                kotlin.srcDir("src/uiTest/kotlin")
                resources.srcDir("src/uiTest/resources")
            }
        }
    }
}

dependencies {
    api(project(":feed"))
    api(libs.androidx.paging.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    testImplementation(project(":feed-test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.paging.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(project(":feed-test"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.paging.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.turbine)
}