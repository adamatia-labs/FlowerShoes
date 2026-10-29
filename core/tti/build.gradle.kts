plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // 화면 진입 시간(TTI) 계측. 어느 feature 에도 속하지 않고 앱 전체를 가로지른다.
    // 구간 기록 · 쏘기 · Room 저장 · 화면 helper 는 commonMain 이고, 부팅 기준 시계만 플랫폼별이다.
    // 기록기를 조립하고 화면 트리에 꽂는 것은 Android 뿐이다(화면별 TTI 는 Android 만 잰다). iOS 는 컴파일만 된다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.tti"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용(번들 SQLite 로 실제 쿼리를 돌린다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            // 디스패처 한정자. 측정 기록은 전부 IO 에서 돈다.
            implementation(project(":common:di"))
            // 로컬 저장. 프로세스가 죽어도 남아 있어야 하므로 메모리가 아니라 DB 다. 플랫폼 빌더가 Room 타입을 그대로 내보낸다.
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            // 그려진 프레임을 잡는 helper 만 Compose 를 쓴다(withFrameNanos · CompositionLocal).
            implementation(compose.runtime)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.room.testing)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// 마이그레이션 테스트가 내보낸 스키마 JSON 을 읽는다.
tasks.withType<Test>().configureEach {
    systemProperty("room.schemaDirectory", "$projectDir/schemas")
}

dependencies {
    // Room 컴파일러는 타깃마다 따로 돌린다.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
