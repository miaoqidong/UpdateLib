import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "xiaofeixia.gesture"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "update.lib.zhushou"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["compileSdkVersion"] as Int
        versionCode = 3
        versionName = "1.0"
    }


    flavorDimensions += "mode"
    productFlavors {

        create("gp_sj") {
            dimension = "mode"
            buildConfigField("Boolean", "isGooglePlay", "true")
        }

        create("hp_sj") {
            dimension = "mode"
            buildConfigField("Boolean", "isGooglePlay", "false")
        }

        all {
            resValue("string", "tip", "hello this is form mainapp")
            buildConfigField("String", "Sign", "\"A\"")
            buildConfigField("Boolean", "isAllFunction", "false")
            buildConfigField("Boolean", "isYouQun", "true")
            manifestPlaceholders["APP_CHANNEL_VALUE"] = "A"
        }
    }


    signingConfigs {

        val properties = Properties()
        val localProperties = rootProject.file("local.properties")

        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }

        register("release") {

            val storeFileName = properties.getProperty("STORE_FILE_NAME")

            if (!storeFileName.isNullOrBlank()) {
                storeFile = file(storeFileName)
                storePassword = properties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = properties.getProperty("STORE_ALIAS")
                keyPassword = properties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".dev"
            isMinifyEnabled = false
            resValue("string", "app_name", "@string/app_name_dev")
            resValue("string", "home_title", "@string/app_name_dev")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":update-java"))
    implementation(project(":update-simple"))
    implementation(project(":update-compose"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}