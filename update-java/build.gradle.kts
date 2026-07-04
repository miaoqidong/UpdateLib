plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mqd.updatejava"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        buildConfig = false
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

// 纯 Java 模块，零额外依赖——只靠 Android SDK
dependencies {
}
