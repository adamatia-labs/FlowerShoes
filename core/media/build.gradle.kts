plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // 완성 PNG(위젯 · 알림 · 목록용, 설계 5-3). 두 플랫폼 모두 화면과 같은 Compose 렌더러로 그린다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.media"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:drawing-engine"))
            implementation(project(":core:drawing-ui"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
    }
}
