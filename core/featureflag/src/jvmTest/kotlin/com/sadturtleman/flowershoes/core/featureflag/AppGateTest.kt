package com.sadturtleman.flowershoes.core.featureflag

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 업데이트 필요 · 점검 중 판단(#72). Remote Config `min_version` · `maintenance` 를 읽는다.
 *
 * 잘못 막으면 앱을 아예 못 쓰게 되므로, 값이 없거나 읽지 못하면 언제나 열어 둔다.
 */
class AppGateTest {

    @Test
    fun `리모트에 아무것도 없으면 열려 있다`() = runTest {
        assertEquals(AppGate.Open, gate(FakeRemote(), appVersion = "1.0.0"))
    }

    @Test
    fun `앱 버전이 최소 버전보다 낮으면 업데이트가 필요하다`() = runTest {
        assertEquals(AppGate.UpdateRequired("2.1.0"), gate(FakeRemote("min_version_prod" to "2.1.0"), appVersion = "2.0.9"))
    }

    @Test
    fun `최소 버전과 같거나 높으면 열려 있다`() = runTest {
        val remote = FakeRemote("min_version_prod" to "2.1.0")
        assertEquals(AppGate.Open, gate(remote, appVersion = "2.1.0"))
        assertEquals(AppGate.Open, gate(remote, appVersion = "2.10.0"))
    }

    /** 글자로 비교하면 "2.10.0" 이 "2.9.0" 보다 낮게 나온다. */
    @Test
    fun `버전은 자리마다 숫자로 비교한다`() = runTest {
        assertEquals(AppGate.UpdateRequired("2.10"), gate(FakeRemote("min_version_prod" to "2.10"), appVersion = "2.9.5"))
    }

    /** Android dev · qa 플레이버는 versionName 에 "-dev" 같은 꼬리가 붙는다. */
    @Test
    fun `버전 꼬리는 비교에서 뺀다`() = runTest {
        assertEquals(AppGate.UpdateRequired("1.2.0"), gate(FakeRemote("min_version_prod" to "1.2.0"), appVersion = "1.1.0-dev"))
        assertEquals(AppGate.Open, gate(FakeRemote("min_version_prod" to "1.2.0"), appVersion = "1.2.0-qa"))
    }

    /** 콘솔에 잘못 적은 값 하나로 모든 사용자가 막히면 안 된다. */
    @Test
    fun `최소 버전을 읽지 못하면 막지 않는다`() = runTest {
        assertEquals(AppGate.Open, gate(FakeRemote("min_version_prod" to "최신"), appVersion = "1.0.0"))
    }

    @Test
    fun `점검 중이면 막는다`() = runTest {
        assertEquals(AppGate.Maintenance, gate(FakeRemote("maintenance_prod" to "true"), appVersion = "1.0.0"))
    }

    /** 점검 중에도 스토어에서 새 버전은 받을 수 있다 — 할 수 있는 일을 먼저 보여 준다. */
    @Test
    fun `업데이트 필요와 점검 중이 겹치면 업데이트가 먼저다`() = runTest {
        val remote = FakeRemote("min_version_prod" to "3.0.0", "maintenance_prod" to "true")
        assertEquals(AppGate.UpdateRequired("3.0.0"), gate(remote, appVersion = "2.0.0"))
    }

    /** 점검이 끝나면 다시 시도했을 때 바로 열려야 한다 — 한 실행 동안 붙잡아 두지 않는다. */
    @Test
    fun `점검이 끝나면 다음 판단에서 열린다`() = runTest {
        val remote = FakeRemote("maintenance_prod" to "true")
        val provider = provider(remote)
        provider.init()
        assertEquals(AppGate.Maintenance, provider.appGate("1.0.0"))

        remote.values["maintenance_prod"] = "false"

        assertEquals(AppGate.Open, provider.appGate("1.0.0"))
    }

    private suspend fun TestScope.gate(remote: RemoteConfigSource, appVersion: String): AppGate {
        val provider = provider(remote)
        provider.init()
        return provider.appGate(appVersion)
    }

    private fun TestScope.provider(remote: RemoteConfigSource): FeatureFlagProvider = createFeatureFlagProvider(
        remote = remote,
        environment = AppEnvironment.PROD,
        stateRecorder = { _, _ -> },
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )
}

private class FakeRemote(vararg values: Pair<String, String>) : RemoteConfigSource {
    val values: MutableMap<String, String> = values.toMap().toMutableMap()
    override suspend fun fetchAndActivate(): Boolean = true
    override fun hasKey(key: String): Boolean = key in values
    override fun getBoolean(key: String): Boolean = values[key]?.toBooleanStrictOrNull() ?: throw IllegalArgumentException(key)
    override fun getLong(key: String): Long = values[key]?.toLongOrNull() ?: throw IllegalArgumentException(key)
    override fun getDouble(key: String): Double = values[key]?.toDoubleOrNull() ?: throw IllegalArgumentException(key)
    override fun getString(key: String): String = values[key].orEmpty()
}
