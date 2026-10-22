package com.sadturtleman.flowershoes.featureflag.domain

/**
 * 플래그 값을 가져오는 곳. 지금은 :featureflag:data 의 샘플 구현이 꽂혀 있고,
 * Firebase Remote Config 를 붙이면 그 자리만 갈아끼운다.
 *
 * 생김새를 FirebaseRemoteConfig 에 맞춰 둔 것은 그때 옮겨 적을 것이 없게 하려는 것이다 —
 * [hasKey] 는 getKeysByPrefix, 나머지는 같은 이름의 typed getter 에 그대로 대응한다.
 *
 * 이 모듈이 Firebase 를 직접 알지 않는 이유는 안드로이드 의존이 들어오면
 * feature:domain 에서 플래그를 읽을 수 없게 되기 때문이다.
 */
interface RemoteConfigSource {

    /**
     * 리모트에서 받아 와 활성화한다. 받아 온 것이 있으면 true.
     *
     * 잦은 호출은 구현이 스스로 걸러야 한다 —
     * Firebase 의 minimumFetchInterval 처럼 왕복 없이 곧바로 돌아오는 것을 전제로 한다.
     */
    suspend fun fetchAndActivate(): Boolean

    /**
     * 활성화된 값 중에 이 키가 있는가.
     *
     * 없는 키를 읽으면 typed getter 가 0 · false · 빈 문자열을 돌려주기 때문에 먼저 물어야 한다 —
     * 그러지 않으면 "리모트에 없다" 와 "0 으로 정했다" 를 가릴 수 없다.
     */
    fun hasKey(key: String): Boolean

    fun getBoolean(key: String): Boolean
    fun getLong(key: String): Long
    fun getDouble(key: String): Double

    /** 리스트 · JSON 객체는 문자열로 내려온다. 파싱은 [FlagKey.serializer] 가 한다. */
    fun getString(key: String): String
}
