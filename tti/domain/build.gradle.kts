plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 안드로이드 의존이 하나도 없어야 하는 모듈이다.
    // 시각도 저장도 포트로만 두고, 실제 구현은 :tti:data 가 꽂는다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
