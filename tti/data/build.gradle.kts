import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sadturtleman.flowershoes.tti.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // DI(Koin). 공유 앱과 같은 그래프에 들어간다.
    implementation(libs.koin.android)
    // 계약(포트) 과 기록기 본체. 주입받는 쪽이 TtiRecorder 를 참조하므로 밖으로 내보낸다.
    api(project(":tti:domain"))

    // 디스패처 한정자. 측정 기록은 전부 IO 에서 돈다.
    implementation(project(":common:di"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // 로컬 저장. 프로세스가 죽어도 남아 있어야 하므로 메모리가 아니라 DB 다.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}
