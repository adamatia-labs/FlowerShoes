plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    // Repository. 로컬 DB 쓰기와 SyncOp 발행을 한 트랜잭션으로 묶고, 받은 Op 을 LWW 로 반영한다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.data"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:database"))
            api(project(":core:sync"))
            api(project(":core:military"))
            api(project(":core:crypto"))
            implementation(libs.androidx.room.runtime)
            // 테스트의 인메모리 DB 도 번들 드라이버를 쓴다.
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // 이 기기에만 저장하는 설정(테마 · 온보딩 진행 · 로컬 신원). 동기화하지 않는다.
            api(libs.androidx.datastore.preferences.core)
            api(libs.okio)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

