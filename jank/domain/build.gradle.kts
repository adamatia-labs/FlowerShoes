plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 안드로이드 의존이 하나도 없어야 하는 모듈이다.
    // 무엇을 언제 모아 내보내는가는 플랫폼과 무관하고, 프레임은 :jank:presentation 이
    // JankStats 의 FrameData 를 JankFrame 으로 옮겨 넣는다.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
