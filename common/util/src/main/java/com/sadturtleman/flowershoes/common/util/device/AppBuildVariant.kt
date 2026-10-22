package com.sadturtleman.flowershoes.common.util.device

/**
 * 이 빌드가 어떤 변형인가. 빌드 타입과 플레이버 한 쌍이다.
 *
 * 이 모듈이 스스로 읽을 수 없는 유일한 값이다 — `BuildConfig` 는 그것을 생성한 모듈의 것이고,
 * 플레이버를 가진 것은 :app 뿐이다. 그래서 :app 이 DI 로 꽂아 준다.
 * (:featureflag 의 [com.sadturtleman.flowershoes.featureflag.domain.AppEnvironment] 가
 * 같은 이유로 같은 길을 쓴다.)
 *
 * @param buildType debug · release 같은 빌드 타입.
 * @param flavor dev · qa · prod 같은 플레이버. 플레이버가 없는 프로젝트면 빈 문자열이다.
 */
data class AppBuildVariant(
    val buildType: String,
    val flavor: String,
) {
    /** 로그 한 줄에 실을 때 쓰는 표기. 예) prodRelease 대신 `prod/release`. */
    override fun toString(): String = if (flavor.isEmpty()) buildType else "$flavor/$buildType"
}
