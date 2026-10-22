import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.common.util"
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
    // 설치 ID 를 남겨 두는 곳. 계약(DataStorage)만 쓰고 저장 엔진은 그 모듈이 가둔다.
    implementation(project(":common:datastore"))

    // 세션 1회 수집은 PackageManager · StatFs 를 타므로 IO 로 보낸다.
    implementation(project(":common:di"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines. 설치 ID 를 한 번만 만들도록 묶는 Mutex 와 디스패처 전환에 쓴다.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
