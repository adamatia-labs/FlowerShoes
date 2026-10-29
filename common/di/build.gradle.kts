plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    // 디스패처 한정자. :core:logging · :core:featureflag · :core:tti 가 iOS 에서도 같은 한정자로 IO 를 받는다.
    // jvm 은 그 모듈들의 테스트용.
    android {
        namespace = "com.sadturtleman.flowershoes.common.di"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // DI(Koin). 공유 앱과 같은 그래프에 들어간다.
            api(libs.koin.core)
            // 한정자가 붙는 대상이 CoroutineDispatcher 라 계약에 드러난다.
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // Dispatchers.Main 의 안드로이드 구현.
            api(libs.kotlinx.coroutines.android)
        }
    }
}
