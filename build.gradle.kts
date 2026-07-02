// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.parcelize) apply false
 
}

@Suppress("unused")
val minSdkVersion by extra(23)
@Suppress("unused")
val compileSdkVersion by extra(36)
@Suppress("unused")
val buildToolVersion by extra("36.0.0")