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

// FileProvider 由宿主项目提供，compileOnly 不打包
dependencies {

    //compileOnly(libs.androidx.core)
    //implementation(libs.androidx.core)
}
