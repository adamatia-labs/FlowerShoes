plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    // 프레임 드랍(jank) 계측. TTI 가 "언제 쓸 수 있게 되는가" 를 잰다면 이쪽은 "쓰는 동안 매끄러운가" 를 잰다.
    // 버킷 · 임계치 규칙(JankReporter)과 화면 이름 · 스크롤 helper 는 commonMain 이고,
    // 프레임을 대는 곳만 플랫폼별이다 — Android 는 JankStats, iOS 는 CADisplayLink 의 표시 간격.
    android {
        namespace = "com.sadturtleman.flowershoes.core.jank"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // jvm 은 테스트용.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // DI(Koin). 공유 앱과 같은 그래프에 들어간다.
            implementation(libs.koin.core)
            // 화면 이름 등록 · 스크롤 감시 helper 만 Compose 를 쓴다.
            implementation(compose.runtime)
            implementation(compose.foundation)
        }
        androidMain.dependencies {
            // JankStats. FrameData 를 JankFrame 으로 옮기고, 화면 이름 · 스크롤을 프레임 꼬리표로 단다.
            api(libs.androidx.metrics.performance)
            // 꼬리표를 달 화면 계층(LocalView).
            implementation(compose.ui)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }
    }
}
