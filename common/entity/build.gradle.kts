plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    // 리모트 컨피그가 JSON 으로 내려주는 값(VO · 변형 enum). :core:featureflag 가 iOS 에서도 읽는다.
    // 안드로이드 앱은 jvm 산출물을 그대로 쓴다.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
