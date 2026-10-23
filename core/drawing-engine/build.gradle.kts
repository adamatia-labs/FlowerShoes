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
            // 저장 형식의 zstd 압축만 밖에서 받는다.
            api(project(":core:compression"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
