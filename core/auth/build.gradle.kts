plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    // 소셜 로그인(카카오 · Apple · Google) 계약. SDK 연결은 플랫폼 구현이 맡는다.
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
