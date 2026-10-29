package com.sadturtleman.flowershoes.core.featureflag

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlin.time.Duration.Companion.hours

/**
 * Firebase Remote Config(GitLive). Firebase 가 설정된 뒤에만 만든다.
 *
 * 잦은 왕복은 [MIN_FETCH_INTERVAL] 이 거른다 — 그 안에서는 네트워크 없이 캐시를 활성화하고 돌아온다.
 * [RemoteConfigSource] 가 전제하는 그대로다. SDK 기본값(12시간)을 쓰지 않는 것은 stable 이 아닌 플래그
 * (타임아웃 · 재시도)가 운영 중에 듣게 하려는 것인데, 너무 줄이면 SDK 가 왕복을 막아(throttle) 예외가 난다 —
 * 그때 제공자는 마지막으로 성공한 값을 쓴다.
 */
class FirebaseRemoteConfigSource(
    private val config: FirebaseRemoteConfig = Firebase.remoteConfig,
) : RemoteConfigSource {

    override suspend fun fetchAndActivate(): Boolean {
        config.fetch(MIN_FETCH_INTERVAL)
        return config.activate()
    }

    /** getKeysByPrefix 는 접두사가 같은 더 긴 키도 돌려주므로 같은 이름이 있는지까지 본다. */
    override fun hasKey(key: String): Boolean = key in config.getKeysByPrefix(key)

    override fun getBoolean(key: String): Boolean = config.getValue(key).asBoolean()
    override fun getLong(key: String): Long = config.getValue(key).asLong()
    override fun getDouble(key: String): Double = config.getValue(key).asDouble()
    override fun getString(key: String): String = config.getValue(key).asString()

    private companion object {
        val MIN_FETCH_INTERVAL = 1.hours
    }
}
