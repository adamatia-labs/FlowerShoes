package com.sadturtleman.flowershoes.featureflag.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * [FeatureFlagProvider] 구현. 바깥에서는 [createFeatureFlagProvider] 로만 만든다.
 *
 * 값을 두 칸으로 나눠 들고 있다 — [stable] 은 [init] 이 받아 둔 그대로 한 실행 내내 쓰고,
 * [volatile] 은 읽을 때마다 새로 가져오되 왕복이 실패하면 마지막으로 성공한 값을 내놓는다.
 * 나누는 이유는 기본값으로 떨어지는 순간이 다르기 때문이다 — 화면 구성을 가르는 값은
 * 도중에 바뀌면 안 되고, 운영값은 바뀌어야 하되 네트워크가 끊겼다고 초기값으로 되돌아가면 곤란하다.
 *
 * 두 칸 모두 해석한 값이 아니라 [JsonElement] 를 담는다. 해석한 값을 담으면 플래그마다 타입이 달라
 * 맵이 Any 를 싣게 되고 꺼낼 때마다 캐스팅이 필요해진다. JsonElement 는 타입이 하나이고,
 * 거기서 값으로 가는 길은 [FlagKey.serializer] 하나뿐이라 캐스팅이 아예 없다.
 *
 * 리모트 왕복은 전부 IO 디스패처에서 돌고, 읽기 실패는 예외 대신 기본값으로 떨어진다 —
 * 플래그를 못 읽었다고 화면이 멈출 이유는 없다.
 */
internal class DefaultFeatureFlagProvider(
    private val remote: RemoteConfigSource,
    private val environment: AppEnvironment,
    private val stateRecorder: FlagStateRecorder,
    private val io: CoroutineDispatcher,
) : FeatureFlagProvider {

    /** [init] 이 받아 둔 원문. 키는 [FlagKey.key] 다. */
    private val stable = ConcurrentHashMap<String, JsonElement>()

    /** 매번 새로 가져오는 값의 마지막 성공본. 왕복이 실패했을 때의 받침이다. */
    private val volatile = ConcurrentHashMap<String, JsonElement>()

    /**
     * 왕복을 한 줄로 세운다. 여러 화면이 동시에 안정화되지 않은 플래그를 읽어도
     * 네트워크에 나가는 것은 한 번에 하나다.
     */
    private val fetchMutex = Mutex()

    /** 리스트 · JSON 객체를 읽는 데 쓴다. 모르는 필드는 버린다 — 콘솔이 앱보다 먼저 나갈 수 있다. */
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun init() {
        val assignments = withContext(io) {
            fetch()
            FlagKey.stableFlags.forEach { flag -> rawElement(flag)?.let { stable[flag.key] = it } }
            // 안정화되지 않은 것도 여기서 한 번 받아 둔다 — 첫 왕복이 실패해도 내놓을 값이 있어야 한다.
            FlagKey.volatileFlags.forEach { flag -> rawElement(flag)?.let { volatile[flag.key] = it } }
            FlagKey.all.associate { flag -> flag.key to describe(flag) }
        }
        // 어떤 배정을 받았는지 남긴다. 부르는 쪽에 맡기지 않는 이유는 [FlagStateRecorder] 에 적어 두었다.
        runCatching { stateRecorder.record(environment, assignments) }
            .onFailure { warn("플래그 상태 기록 실패", it) }
    }

    override suspend fun <T> get(flag: FlagKey<T>): T {
        if (flag.stable) {
            // init 전이거나 읽지 못했으면 기본값이다. 여기서 왕복하지 않는 것이 stable 의 뜻이다.
            return decode(flag, stable[flag.key])
        }
        return withContext(io) {
            // 왕복이 실패했으면 마지막으로 성공한 값을 쓴다.
            // 네트워크가 끊겼다고 운영값이 초기값으로 되돌아가면, 고쳐 둔 타임아웃이 그 자리에서 풀린다.
            val fresh = if (fetch()) rawElement(flag)?.also { volatile[flag.key] = it } else null
            decode(flag, fresh ?: volatile[flag.key])
        }
    }

    /**
     * 리모트에 한 번 다녀온다. 돌려주는 값은 "받아 온 것이 있는가" 가 아니라 "왕복 자체가 됐는가" 다 —
     * 받아 올 것이 없어도(이미 최신이어도) 읽기는 정상이고, 예외가 났을 때만 받침이 필요하다.
     */
    private suspend fun fetch(): Boolean = fetchMutex.withLock {
        runCatching { remote.fetchAndActivate() }
            .onFailure { warn("리모트 컨피그 fetch 실패", it) }
            .isSuccess
    }

    /**
     * 이 환경에서 [flag] 에 해당하는 원문. 어느 환경에도 값이 없으면 null 이다.
     *
     * 값을 [JsonElement] 로 맞춰 두는 이유는 원시 타입과 JSON 을 한 길로 모으기 위해서다 —
     * 타입별로 따로 꺼내 캐스팅하면 그 캐스팅이 T 와 어긋나도 실행할 때까지 드러나지 않는다.
     */
    private fun rawElement(flag: FlagKey<*>): JsonElement? {
        for (target in environment.fallbackChain()) {
            val remoteKey = target.remoteKey(flag)
            // 없는 키를 읽으면 0 · false 가 돌아오므로, 있는지부터 묻고 없으면 다음 환경으로 넘어간다.
            if (!remote.hasKey(remoteKey)) continue
            val element = runCatching { element(flag, remoteKey) }.getOrElse {
                warn("플래그 해석 실패: $remoteKey", it)
                null
            }
            if (element != null) return element
        }
        return null
    }

    /** 리모트 값 하나를 [FlagKey.serializer] 가 읽을 수 있는 모양으로 꺼낸다. */
    private fun element(flag: FlagKey<*>, remoteKey: String): JsonElement =
        when (flag.serializer.descriptor.kind) {
            PrimitiveKind.BOOLEAN -> JsonPrimitive(remote.getBoolean(remoteKey))
            PrimitiveKind.INT, PrimitiveKind.LONG -> JsonPrimitive(remote.getLong(remoteKey))
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> JsonPrimitive(remote.getDouble(remoteKey))
            // 변형 이름은 따옴표 없이 적히므로 JSON 으로 파싱하지 않고 문자열 그대로 싣는다.
            PrimitiveKind.STRING, SerialKind.ENUM -> JsonPrimitive(remote.getString(remoteKey))
            // 리스트 · JSON 객체는 콘솔에 JSON 으로 적혀 있다.
            else -> json.parseToJsonElement(remote.getString(remoteKey))
        }

    /**
     * 원문을 이 플래그의 타입으로 읽는다. 원문이 없거나 읽지 못하면 기본값이다.
     *
     * 어느 타입으로 읽을지는 [FlagKey.serializer] 가 들고 있다. 키가 타입을 함께 지니므로
     * 여기에 캐스팅이 없고, 선언한 T 와 어긋나는 조합은 애초에 쓸 수 없다.
     */
    private fun <T> decode(flag: FlagKey<T>, raw: JsonElement?): T {
        if (raw == null) return flag.defaultValue
        return runCatching { json.decodeFromJsonElement(flag.serializer, raw) }.getOrElse {
            warn("플래그 해석 실패: ${flag.key}", it)
            flag.defaultValue
        }
    }

    /** 배정을 남길 때 쓰는 표현. 되짚어 읽는 값이라 타입과 상관없이 글자면 된다. */
    private fun describe(flag: FlagKey<*>): String =
        decode(flag, stable[flag.key] ?: volatile[flag.key]).toString()

    /**
     * 플래그를 읽지 못했다는 사실만 남긴다.
     *
     * 플랫폼 로거를 쓰지 않는 것은 이 모듈이 어느 플랫폼에서도 그대로 돌아야 하기 때문이다.
     * 안드로이드에서는 표준 출력도 Logcat 으로 들어온다.
     */
    private fun warn(what: String, cause: Throwable) {
        println("$TAG: $what (${cause.javaClass.simpleName}: ${cause.message})")
    }

    private companion object {
        const val TAG = "FLAG"
    }
}
