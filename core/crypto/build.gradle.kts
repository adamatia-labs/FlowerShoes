plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    // 기기 키 · 커플 키 · SAS. libsodium(X25519 · XChaCha20-Poly1305 · Ed25519) 위에 얹는다.
    // Android 타깃이 따로 있어야 libsodium 의 Android(ABI별) 산출물을 받는다. jvm 은 테스트용(JNA 데스크톱 라이브러리).
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
    android {
        namespace = "com.sadturtleman.flowershoes.core.crypto"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.libsodium.bindings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
