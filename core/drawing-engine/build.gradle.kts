plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    // 그림 문서 · 인코딩 · 간소화 · 리플레이 타임라인. UI 가 없다 — 렌더러는 Compose 쪽에서 이 결과를 그린다.
    // iOS 타깃은 macOS 에서만 컴파일된다(Windows 에서는 건너뛴다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            // 순수 계산만 한다. 의존 없음.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
