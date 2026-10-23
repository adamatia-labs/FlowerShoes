plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(17)

    // 로컬 DB(원본). Entity · DAO · @Database 는 commonMain, DB 파일 위치만 플랫폼별이다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.database"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용(BundledSQLiteDriver 로 실제 SQLite 에 쿼리를 돌린다).
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // 빌더 · DAO 가 Room 타입을 그대로 내보낸다.
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room 컴파일러는 타깃마다 따로 돌린다.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
