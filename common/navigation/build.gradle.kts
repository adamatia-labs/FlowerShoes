plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // NavRoute.args 직렬화
    api(libs.kotlinx.serialization.json)

    // NavigationHelper 가 Flow 를 계약에 담는다.
    api(libs.kotlinx.coroutines.core)
}
