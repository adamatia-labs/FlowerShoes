package com.sadturtleman.flowershoes.featureflag.data

import com.sadturtleman.flowershoes.featureflag.domain.RemoteConfigSource
import javax.inject.Inject

/**
 * 붙일 리모트 컨피그 서비스가 없어 값을 앱 안에 두는 샘플 구현.
 *
 * 실제 Firebase Remote Config 가 하는 일을 그대로 흉내 낸다 — 값은 전부 문자열로 들고 있고,
 * 어느 타입으로 읽을지는 읽는 쪽([com.sadturtleman.flowershoes.featureflag.domain.FlagKey.serializer])이
 * 정한다. 그래서 Firebase 를 붙일 때 [RemoteConfigSource] 구현 하나만 갈아끼우면 되고,
 * 읽는 쪽 코드는 손대지 않는다.
 *
 * 환경 꼬리도 실제와 같다. 모든 키를 세 번씩 적지 않는 것도 같다 —
 * 읽는 쪽이 dev → qa → prod 순으로 내려가며 찾으므로 달라지는 것만 적어 두면 된다.
 */
internal class SampleRemoteConfigSource @Inject constructor() : RemoteConfigSource {

    /** 여기 없는 키는 "리모트가 모르는 플래그" 이고, 읽는 쪽이 기본값으로 떨어뜨린다. */
    private val values: Map<String, String> = mapOf(
        // prod — 받침이 되는 값. dev · qa 가 따로 적지 않은 것은 여기까지 내려와 읽는다.
        "new_onboarding_prod" to "false",
        "home_page_size_prod" to "13",
        "network_retry_count_prod" to "3",
        "fetch_timeout_ms_prod" to "5000",
        "retry_backoff_ms_prod" to "[1000,2000,4000]",
        "home_banner_prod" to """{"title":"가을 특별전","linkUrl":"https://www.eflower.go.kr"}""",
        "ab_home_default_layout_prod" to "list",

        // qa — 검증할 것만 다르게 둔다.
        "new_onboarding_qa" to "true",
        "ab_home_default_layout_qa" to "grid",

        // dev — 만드는 중인 것을 켜 두고, 왕복은 짧게 끊는다.
        "home_page_size_dev" to "5",
        "network_retry_count_dev" to "1",
        "fetch_timeout_ms_dev" to "3000",
        "retry_backoff_ms_dev" to "[200,400]",
        "home_banner_dev" to """{"title":"개발 빌드입니다"}""",
    )

    /** 네트워크가 없으므로 늘 성공하고, 늘 "새로 받아 온 것은 없다" 고 답한다. */
    override suspend fun fetchAndActivate(): Boolean = false

    override fun hasKey(key: String): Boolean = key in values

    override fun getBoolean(key: String): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: throw IllegalArgumentException("boolean 이 아니다: $key")

    override fun getLong(key: String): Long =
        values[key]?.toLongOrNull() ?: throw IllegalArgumentException("long 이 아니다: $key")

    override fun getDouble(key: String): Double =
        values[key]?.toDoubleOrNull() ?: throw IllegalArgumentException("double 이 아니다: $key")

    override fun getString(key: String): String = values[key].orEmpty()
}
