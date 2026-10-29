plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.sadturtleman.flowershoes.feature.pairing"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // feature 는 core 만 본다. 다른 feature 로의 이동은 :composeApp 이 콜백으로 잇는다.
            implementation(project(":core:designsystem"))
            implementation(project(":core:data"))
            implementation(project(":core:logging"))
            implementation(project(":core:auth"))
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        // 화면 렌더 확인(넘겨받는 중처럼 실기기에서 잠깐 스치는 화면을 이미지로).
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}
