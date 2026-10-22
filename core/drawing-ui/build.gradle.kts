plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // 그림 렌더러 · 리플레이 플레이어(Compose Canvas). 편지 · 추억 · 그림 화면이 함께 쓴다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.drawingui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            api(project(":core:drawing-engine"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.uiToolingPreview)
        }
    }
}
