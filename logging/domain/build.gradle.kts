plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 안드로이드 의존이 하나도 없어야 하는 모듈이다.
    // 무엇을 언제 기록하는지는 플랫폼과 무관하고, 시각(BizLogClock) · 저장(BizLogStore) ·
    // 전송(BizLogShooter) 을 포트로 두어 바깥이 갈아끼운다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
