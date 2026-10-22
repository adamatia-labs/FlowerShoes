import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sadturtleman.flowershoes.jank.presentation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 화면이 JankReporter 를 CompositionLocal 로 받으므로 밖으로 내보낸다.
    api(project(":jank:domain"))

    // JankStats. FrameData 를 도메인 모양(JankFrame)으로 옮기는 것이 이 모듈의 일이다.
    api(libs.androidx.metrics.performance)

    // 화면 이름 등록 · 스크롤 감시 helper 만 Compose 를 쓴다.
    // Hilt 는 없다 — 기록기는 Activity 가 주입받아 CompositionLocal 로 내려준다.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
}
