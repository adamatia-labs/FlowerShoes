plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // HomeContentVO 가 리모트 컨피그 값(HomeBannerVO)을 그대로 싣고 나간다.
    api(project(":common:entity"))
}
