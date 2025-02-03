// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.nexus.publish)
}

group = "me.tatarka.android.feed"
version = "0.1.0"

nexusPublishing {
    repositories {
        sonatype()
    }
}