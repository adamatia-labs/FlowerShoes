import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.featureflag.data"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 계약(포트) 과 제공자 본체. 주입받는 쪽이 FeatureFlagProvider · FlagKey 를 참조하므로 밖으로 내보낸다.
    api(project(":featureflag:domain"))

    // 디스패처 한정자. 리모트 왕복은 전부 IO 에서 돈다.
    implementation(project(":common:di"))

    // 플래그 · 실험 배정을 남기는 곳. 요구사항상 사용자 식별자와 함께 나가야 한다.
    implementation(project(":logging:domain"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
