plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // UseCase 가 HomeContentVO 를 그대로 돌려주므로 api 로 내보낸다.
    api(project(":home:entity"))
    api(project(":common:domain"))

    // 배너 값을 플래그가 정한다. 순수 코틀린이라 도메인에서 그대로 쓴다.
    implementation(project(":featureflag:domain"))
    implementation(libs.javax.inject)
}
