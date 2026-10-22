plugins {
    alias(libs.plugins.kotlin.jvm)
    // 플래그 값의 타입을 KSerializer 로 들고 다닌다. FlagKey 안의 @Serializable 참조에 필요하다.
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 안드로이드 의존이 하나도 없어야 하는 모듈이다 —
    // presentation 뿐 아니라 feature:domain 의 UseCase 도 플래그를 읽기 때문이다.
    // 값 출처(RemoteConfigSource) 와 상태 기록(FlagStateRecorder) 은 포트로 두어 바깥이 갈아끼운다.
    api(libs.kotlinx.coroutines.core)

    // 키가 자기 타입을 KSerializer 로 들고 있어야 List<T> 와 JSON 객체까지 타입을 잃지 않는다.
    // Class<T> 로는 제네릭이 지워져 원소 타입을 알 수 없다.
    api(libs.kotlinx.serialization.json)

    // JSON 으로 내려오는 플래그 값(VO · 변형 enum).
    api(project(":common:entity"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
