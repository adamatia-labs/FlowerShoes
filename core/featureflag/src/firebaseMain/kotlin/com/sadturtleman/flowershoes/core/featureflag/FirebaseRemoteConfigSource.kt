package com.sadturtleman.flowershoes.core.featureflag

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig

/**
 * Firebase Remote Config(GitLive). Firebase 가 설정된 뒤에만 만든다.
 *
 * 잦은 왕복은 SDK 의 minimumFetchInterval(기본 12시간)이 거른다 — 그 안에서는 [fetchAndActivate] 가
 * 네트워크 없이 캐시를 활성화하고 돌아온다. [RemoteConfigSource] 가 전제하는 그대로다.
 */
class FirebaseRemoteConfigSource(
    private val config: FirebaseRemoteConfig = Firebase.remoteConfig,
) : RemoteConfigSource {

    override suspend fun fetchAndActivate(): Boolean = config.fetchAndActivate()

    /** getKeysByPrefix 는 접두사가 같은 더 긴 키도 돌려주므로 같은 이름이 있는지까지 본다. */
    override fun hasKey(key: String): Boolean = key in config.getKeysByPrefix(key)

    override fun getBoolean(key: String): Boolean = config.getValue(key).asBoolean()
    override fun getLong(key: String): Long = config.getValue(key).asLong()
    override fun getDouble(key: String): Double = config.getValue(key).asDouble()
    override fun getString(key: String): String = config.getValue(key).asString()
}
