plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    // 동기화 엔진. 번호 · 커서 · 재전송 판단 · 충돌 해결은 순수 코틀린으로 두고 테스트로 묶는다.
    // 우편함(Firestore) 전송은 이 위에 얹는 어댑터가 맡는다.
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
