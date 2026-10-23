plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    // 순수 코틀린 도메인 모델. 안드로이드 앱은 jvm 산출물을 그대로 쓴다.
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
