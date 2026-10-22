plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    // 기기 키 · 커플 키 · SAS · 복구 코드. libsodium(X25519 · XChaCha20-Poly1305 · Ed25519 · Argon2id) 위에 얹는다.
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
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
