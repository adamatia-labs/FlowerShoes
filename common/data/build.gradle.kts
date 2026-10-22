import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.common.data"
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
    // 여러 feature 가 함께 쓰는 저장소(계약은 :common:domain)의 구현이 여기에 온다.
    // 한 feature 만 쓰는 조회는 그 feature 의 :data 모듈에 둔다.
    implementation(project(":common:domain"))
    // 공용 매퍼가 반환 타입으로 노출한다.
    api(project(":common:entity"))

    // 디스패처 한정자 · 바인딩
    implementation(project(":common:di"))

    // 통신 스택. Retrofit 타입이 api 로 딸려온다.
    implementation(project(":common:network"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // 목록 페이지네이션
    implementation(libs.androidx.paging.common)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
