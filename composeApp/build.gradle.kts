plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // 앱 셸: 하단 탭(홈 · 캘린더 · 편지 · 추억), 화면 이동, DI 조립. Android 는 :app, iOS 는 iosApp 이 이것을 띄운다.
    android {
        namespace = "com.sadturtleman.flowershoes.app"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:data"))
            implementation(project(":core:database"))
            implementation(project(":core:auth"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:home"))
            implementation(project(":feature:calendar"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jb.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
