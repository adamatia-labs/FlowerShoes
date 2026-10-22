plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
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
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
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
            implementation(project(":feature:letter"))
            implementation(project(":feature:drawing"))
            implementation(project(":feature:memory"))
            implementation(project(":feature:pairing"))
            api(project(":feature:settings"))
            api(project(":core:widget-bridge"))
            implementation(project(":core:backup"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jb.lifecycle.runtime.compose)
            // 시스템 뒤로 가기(Android) · 스와이프 뒤로(iOS)로 덮어 띄운 화면을 닫는다.
            implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.composeMultiplatform.get()}")
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            // Firebase 설정(google-services.json)이 있을 때만 쓰인다.
            implementation(project(":core:firebase"))
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation("com.google.firebase:firebase-common")
            implementation("com.google.firebase:firebase-storage")
            implementation(libs.play.services.blockstore)
            implementation(libs.kotlinx.coroutines.play.services)
        }
    }
}
