plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kakario.minigames"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kakario.minigames"
        minSdk = 26
        targetSdk = 35
        // GitHub 자동 빌드 번호를 버전으로 사용 (새 APK가 기존 앱 위에 업데이트되도록)
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // 저장소에 들어 있는 고정 키 — 빌드마다 서명이 바뀌어 재설치가 필요해지는 문제 방지
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 오닉스 이잉크 화면 제어 (다른 기기에서는 아무 일도 안 함)
    implementation("com.onyx.android.sdk:onyxsdk-base:1.7.7") { isTransitive = false }
    implementation("com.onyx.android.sdk:onyxsdk-device:1.2.31") { isTransitive = false }
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
}
