plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // Figma 토큰(색 · 글자 · 간격 · 그림자) + 테마 + 🧩 Components. 모든 feature 가 이것만 보고 그린다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.designsystem"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // composeResources(글꼴 · 아이콘)를 Android 자산으로 싣는다.
        androidResources { enable = true }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        // 테마 v2 토큰 값 확인 + 4개 모드 견본 그림(build/render).
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

// Noto Sans KR(SIL OFL, NOTO_SANS_KR_OFL.txt) · Figma 아이콘은 이 모듈 안에서만 쓴다 — 화면은 Kk* 컴포넌트를 쓴다.
compose.resources {
    packageOfResClass = "com.sadturtleman.flowershoes.core.designsystem.res"
    publicResClass = false
}
