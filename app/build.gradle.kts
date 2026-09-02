plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.gsupl.seatbooking"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.gsupl.seatbooking"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    // OKHttp 网络请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JSON
    implementation("org.json:json:20240303")
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // WorkManager 兜底（Alarm杀了还有它）
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
