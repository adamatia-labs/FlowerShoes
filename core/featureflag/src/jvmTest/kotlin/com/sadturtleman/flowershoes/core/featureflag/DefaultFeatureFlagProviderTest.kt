package com.sadturtleman.flowershoes.core.featureflag

import com.sadturtleman.flowershoes.common.entity.featureflag.HomeBannerVO
import com.sadturtleman.flowershoes.common.entity.featureflag.HomeLayoutVariant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 플래그 읽기 회귀 테스트.
 *
 * 잘못 읽은 플래그는 예외 대신 기본값으로 조용히 떨어지므로, 어긋나도 화면에는 아무 표시가 나지 않는다.
 * 그래서 "왕복을 몇 번 했는가" 와 "어떤 키를 찾았는가" 를 가짜 리모트가 세어 둔다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFeatureFlagProviderTest {

    @Test
    fun `안정화된 플래그는 init 이 받아 둔 값을 왕복 없이 돌려준다`() = runTest {
        val remote = FakeRemoteConfigSource("home_page_size_prod" to "7")
        val provider = provider(remote)
        provider.init()
        val afterInit = remote.fetchCount

        repeat(3) { assertEquals(7, provider.get(FlagKey.HomePageSize)) }

        assertEquals(afterInit, remote.fetchCount)
    }

    @Test
    fun `안정화되지 않은 플래그는 읽을 때마다 다시 받아 온다`() = runTest {
        val remote = FakeRemoteConfigSource("fetch_timeout_ms_prod" to "1500")
        val provider = provider(remote)
        provider.init()
        val afterInit = remote.fetchCount

        assertEquals(1500L, provider.get(FlagKey.FetchTimeoutMillis))
        remote.values["fetch_timeout_ms_prod"] = "2500"
        assertEquals(2500L, provider.get(FlagKey.FetchTimeoutMillis))

        assertEquals(afterInit + 2, remote.fetchCount)
    }

    /** 안정화된 플래그가 한 실행 안에서 값을 바꾸면 같은 사용자가 두 화면을 다르게 본다. */
    @Test
    fun `안정화된 플래그는 리모트가 바뀌어도 그 실행 동안 그대로다`() = runTest {
        val remote = FakeRemoteConfigSource("home_page_size_prod" to "7")
        val provider = provider(remote)
        provider.init()

        remote.values["home_page_size_prod"] = "99"

        assertEquals(7, provider.get(FlagKey.HomePageSize))
    }

    // ---- 타입 ----

    /** 키가 KSerializer 를 들고 있어야 원소 타입까지 지켜진다. Class 로는 List<Long> 의 Long 이 지워진다. */
    @Test
    fun `리스트와 JSON 객체도 선언한 타입 그대로 읽힌다`() = runTest {
        val provider = provider(
            FakeRemoteConfigSource(
                "retry_backoff_ms_prod" to "[300,600,1200]",
                "home_banner_prod" to """{"title":"가을 특별전","linkUrl":"https://example.com"}""",
            )
        )
        provider.init()

        assertEquals(listOf(300L, 600L, 1200L), provider.get(FlagKey.RetryBackoffMillis))
        assertEquals(
            HomeBannerVO(title = "가을 특별전", linkUrl = "https://example.com"),
            provider.get(FlagKey.HomeBanner),
        )
    }

    /** 콘솔의 변형 이름을 enum 으로 읽는다 — 쓰는 쪽의 when 을 컴파일러가 검사할 수 있게 된다. */
    @Test
    fun `AB 변형은 enum 으로 읽힌다`() = runTest {
        val provider = provider(FakeRemoteConfigSource("ab_home_default_layout_prod" to "grid"))
        provider.init()

        assertEquals(HomeLayoutVariant.GRID, provider.get(FlagKey.HomeLayoutAb))
    }

    @Test
    fun `원시 타입은 리모트의 typed getter 로 읽는다`() = runTest {
        val remote = FakeRemoteConfigSource(
            "new_onboarding_prod" to "true",
            "home_page_size_prod" to "21",
            "fetch_timeout_ms_prod" to "9000",
        )
        val provider = provider(remote)
        provider.init()

        assertTrue(provider.get(FlagKey.NewOnboarding))
        assertEquals(21, provider.get(FlagKey.HomePageSize))
        assertEquals(9_000L, provider.get(FlagKey.FetchTimeoutMillis))
        // 문자열을 JSON 으로 파싱해 읽지 않는다 — 콘솔 값은 따옴표 없이 적히기 때문이다.
        assertTrue(remote.readAsString.isEmpty())
    }

    // ---- 환경 ----

    @Test
    fun `환경마다 다른 값을 읽는다`() = runTest {
        val values = arrayOf("home_page_size_dev" to "5", "home_page_size_prod" to "13")
        val dev = provider(FakeRemoteConfigSource(*values), AppEnvironment.DEV)
        val prod = provider(FakeRemoteConfigSource(*values), AppEnvironment.PROD)
        dev.init()
        prod.init()

        assertEquals(5, dev.get(FlagKey.HomePageSize))
        assertEquals(13, prod.get(FlagKey.HomePageSize))
    }

    /** 모든 키를 환경 수만큼 적어 두지 않아도 되게 하려는 것이다. */
    @Test
    fun `자기 환경에 값이 없으면 아래 환경에서 주워 쓴다`() = runTest {
        val dev = provider(FakeRemoteConfigSource("home_page_size_prod" to "13"), AppEnvironment.DEV)
        dev.init()

        assertEquals(13, dev.get(FlagKey.HomePageSize))
    }

    /** dev 에서 켜 본 실험이 prod 사용자에게 새면 안 된다. */
    @Test
    fun `prod 는 위 환경 값을 보지 않는다`() = runTest {
        val prod = provider(FakeRemoteConfigSource("new_onboarding_dev" to "true"), AppEnvironment.PROD)
        prod.init()

        assertFalse(prod.get(FlagKey.NewOnboarding))
    }

    // ---- 못 읽었을 때 ----

    @Test
    fun `리모트가 모르는 키는 기본값이다`() = runTest {
        val provider = provider(FakeRemoteConfigSource())
        provider.init()

        assertEquals(FlagKey.HomePageSize.defaultValue, provider.get(FlagKey.HomePageSize))
        assertEquals(FlagKey.RetryBackoffMillis.defaultValue, provider.get(FlagKey.RetryBackoffMillis))
        assertEquals(FlagKey.HomeBanner.defaultValue, provider.get(FlagKey.HomeBanner))
    }

    /** 잘못 적힌 값이 예외로 터지면 그 화면이 통째로 죽는다. */
    @Test
    fun `읽지 못하는 값이 오면 기본값으로 떨어진다`() = runTest {
        val provider = provider(
            FakeRemoteConfigSource(
                "home_page_size_prod" to "열세개",
                "home_banner_prod" to "{ 망가진 json",
                "ab_home_default_layout_prod" to "Grid",
            )
        )
        provider.init()

        assertEquals(13, provider.get(FlagKey.HomePageSize))
        assertEquals(HomeBannerVO(title = ""), provider.get(FlagKey.HomeBanner))
        // 콘솔에 대문자로 적히면 그 순간 해석이 실패하고 기준 변형으로 떨어진다.
        assertEquals(HomeLayoutVariant.LIST, provider.get(FlagKey.HomeLayoutAb))
    }

    @Test
    fun `init 전에 읽으면 기본값이고 왕복하지 않는다`() = runTest {
        val remote = FakeRemoteConfigSource("home_page_size_prod" to "7")
        val provider = provider(remote)

        assertEquals(13, provider.get(FlagKey.HomePageSize))
        assertEquals(0, remote.fetchCount)
    }

    @Test
    fun `왕복이 실패해도 예외 대신 기본값으로 떨어진다`() = runTest {
        val remote = FakeRemoteConfigSource().apply { failing = true }
        val provider = provider(remote)
        provider.init()

        assertFalse(provider.get(FlagKey.NewOnboarding))
        assertEquals(13, provider.get(FlagKey.HomePageSize))
    }

    /** 네트워크가 끊겼다고 고쳐 둔 운영값이 초기값으로 되돌아가면 곤란하다. */
    @Test
    fun `왕복이 실패하면 마지막으로 성공한 값을 내놓는다`() = runTest {
        val remote = FakeRemoteConfigSource("fetch_timeout_ms_prod" to "1500")
        val provider = provider(remote)
        provider.init()
        assertEquals(1500L, provider.get(FlagKey.FetchTimeoutMillis))

        remote.failing = true
        remote.values.clear()

        assertEquals(1500L, provider.get(FlagKey.FetchTimeoutMillis))
    }

    // ---- 배정 기록 ----

    @Test
    fun `init 은 이 사용자가 받은 배정을 빠짐없이 남긴다`() = runTest {
        val recorder = RecordingFlagStateRecorder()
        val provider = provider(
            remote = FakeRemoteConfigSource("ab_home_default_layout_dev" to "grid"),
            environment = AppEnvironment.DEV,
            recorder = recorder,
        )
        provider.init()

        assertEquals(AppEnvironment.DEV, recorder.environment)
        assertEquals(FlagKey.all.map { it.key }.toSet(), recorder.assignments.keys)
        assertEquals("GRID", recorder.assignments[FlagKey.HomeLayoutAb.key])
        // 리모트가 모르는 것도 "기본값을 받았다" 로 남아야 배정을 되짚을 수 있다.
        assertEquals("13", recorder.assignments[FlagKey.HomePageSize.key])
    }

    /** 카탈로그에 적은 stable 과 목록이 어긋나면 그 플래그가 조용히 기본값이 된다. */
    @Test
    fun `카탈로그의 stable 구분과 목록이 어긋나지 않는다`() {
        assertEquals(FlagKey.all.filter { it.stable }, FlagKey.stableFlags)
        assertEquals(FlagKey.all.filterNot { it.stable }, FlagKey.volatileFlags)
        assertEquals(FlagKey.all.size, FlagKey.all.map { it.key }.toSet().size)
    }

    private fun TestScope.provider(
        remote: RemoteConfigSource,
        environment: AppEnvironment = AppEnvironment.PROD,
        recorder: FlagStateRecorder = FlagStateRecorder { _, _ -> },
    ): FeatureFlagProvider = createFeatureFlagProvider(
        remote = remote,
        environment = environment,
        stateRecorder = recorder,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )
}

/**
 * 왕복 횟수를 세고, 값과 실패 여부를 손으로 바꿀 수 있는 리모트.
 * 잘못 적힌 값에 던지는 것까지 실제 구현과 맞춰야 기본값으로 떨어지는지를 여기서 볼 수 있다.
 */
private class FakeRemoteConfigSource(vararg values: Pair<String, String>) : RemoteConfigSource {
    val values: MutableMap<String, String> = values.toMap().toMutableMap()
    var failing = false
    var fetchCount = 0
        private set

    /** 문자열로 읽어 간 키. 원시 타입이 typed getter 를 지나갔는지 보는 데 쓴다. */
    val readAsString = mutableListOf<String>()

    override suspend fun fetchAndActivate(): Boolean {
        fetchCount++
        if (failing) throw IllegalStateException("network down")
        return true
    }

    override fun hasKey(key: String): Boolean = key in values

    override fun getBoolean(key: String): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: throw IllegalArgumentException(key)

    override fun getLong(key: String): Long =
        values[key]?.toLongOrNull() ?: throw IllegalArgumentException(key)

    override fun getDouble(key: String): Double =
        values[key]?.toDoubleOrNull() ?: throw IllegalArgumentException(key)

    override fun getString(key: String): String {
        readAsString += key
        return values[key].orEmpty()
    }
}

private class RecordingFlagStateRecorder : FlagStateRecorder {
    var environment: AppEnvironment? = null
        private set
    var assignments: Map<String, String> = emptyMap()
        private set

    override fun record(environment: AppEnvironment, assignments: Map<String, String>) {
        this.environment = environment
        this.assignments = assignments
    }
}
