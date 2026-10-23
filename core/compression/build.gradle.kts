plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    // zstd 압축(그림 문서 · 백업). Android · jvm 은 zstd-jni, iOS 는 Swift 의 libzstd 를 앱 시작 때 꽂는다.
    android {
        namespace = "com.sadturtleman.flowershoes.core.compression"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
            implementation("${libs.zstd.jni.get()}@aar")
        }
        jvmMain.dependencies {
            implementation(libs.zstd.jni)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}
