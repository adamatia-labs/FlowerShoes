import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.common.presentation"
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
        // MviViewModel 이 explicit backing field(`val uiState: StateFlow<S> field = ...`) 를 쓴다.
        // 선언 모듈과 소비 모듈 양쪽에 같은 플래그가 있어야 메타데이터를 읽을 수 있다.
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}

dependencies {
    // 모든 feature:presentation 이 common:presentation 하나만 참조하면
    // Compose / lifecycle / Hilt 진입점이 함께 딸려오도록 api 로 노출한다.
    api(project(":common:domain"))

    // NavigationHelperImpl · LocalNavigationHelper 가 계약을 그대로 내보낸다.
    api(project(":common:navigation"))

    // 이미지 로딩 구간을 재는 helper. 화면이 그대로 쓰므로 api 로 내보낸다.
    // (:tti:domain 은 여기에 api 로 딸려 온다 — 화면이 TtiTimeline 을 직접 넘긴다)
    api(project(":tti:presentation"))

    // 스크롤 구간의 프레임 드랍을 재는 helper. 목록 화면이 그대로 쓰므로 api 로 내보낸다.
    api(project(":jank:presentation"))

    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    // 이미지 로딩. 여러 화면이 같은 컴포넌트를 쓰므로 여기서 한 번만 붙인다.
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.hilt.navigation.compose)

    // Coroutines
    api(libs.kotlinx.coroutines.android)
}
