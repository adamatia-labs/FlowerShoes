package com.sadturtleman.flowershoes.common.navigation

import kotlinx.serialization.json.Json

/**
 * 단일 통합 네비게이션 단위.
 *
 * - [path]: 이동 대상 페이지의 식별자. 앱 내 이동과 deep-link 양쪽에서 같은 값을 사용한다. 예: "/home", "/detail"
 * - [args]: 페이지가 요구하는 인자. 모두 String 으로 직렬화된 형태로 전달되며, 복잡 타입은 JSON 문자열로 인코딩한다.
 */
data class NavRoute(
    val path: String,
    val args: Map<String, String> = emptyMap(),
)

/**
 * 각 페이지별로 정의되는 typed argument 의 마커.
 * feature/domain 모듈에서 본 인터페이스를 구현한 object/data class 를 정의하고,
 * [toRoute] 안에서 자기 인자를 [NavRoute.args] 로 직렬화한다.
 */
interface Page {
    fun toRoute(): NavRoute
}

/**
 * NavRoute.args 인코딩/디코딩에 공용으로 사용하는 Json 인스턴스.
 * - ignoreUnknownKeys: 백스택에 직렬화되어 남은 인스턴스가 스키마 변경 후에도 최대한 살아남도록.
 */
val NavRouteJson: Json = Json { ignoreUnknownKeys = true }
