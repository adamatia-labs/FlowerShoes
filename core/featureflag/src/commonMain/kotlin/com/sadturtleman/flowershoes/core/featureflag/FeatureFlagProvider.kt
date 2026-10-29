package com.sadturtleman.flowershoes.core.featureflag

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 피처 플래그 · AB 테스트를 읽는 곳.
 *
 * 앱 전체에 하나만 있고 DI 로 주입받는다. 순수 코틀린이라 presentation 과 feature:domain 양쪽에서
 * 같은 인스턴스를 생성자 주입으로 받는다.
 *
 * ```kotlin
 * class GetHomeContentUseCase(
 *     private val featureFlags: FeatureFlagProvider,
 * ) {
 *     suspend operator fun invoke() = repository.getHomeContent(
 *         pageSize = featureFlags.get(FlagKey.HomePageSize),
 *     )
 * }
 * ```
 */
interface FeatureFlagProvider {

    /**
     * 리모트에서 한 번 받아 와 [FlagKey.stableFlags] 를 메모리에 채운다.
     * Application 이 뜰 때 부른다.
     *
     * 받아 오지 못해도 예외를 던지지 않는다 — 그때는 모든 플래그가 [FlagKey.defaultValue] 로 읽힌다.
     * 값이 정해진 뒤에는 이 사용자가 어떤 플래그 · 실험에 놓였는지를 [FlagStateRecorder] 로 남긴다.
     */
    suspend fun init()

    /**
     * 플래그 하나를 읽는다.
     *
     * [FlagKey.stable] 이면 [init] 이 받아 둔 값을 그대로 돌려주고(왕복 없음),
     * 아니면 그 자리에서 다시 받아 온다.
     */
    suspend fun <T> get(flag: FlagKey<T>): T
}

/**
 * 이 실행에서 사용자가 어떤 플래그 · 실험에 놓였는지 남기는 포트.
 *
 * 제공자가 [com.sadturtleman.flowershoes.core.logging.BizLogger] 를 직접 부르지 않고 포트를 두는 것은,
 * 테스트가 "무엇을 남겼는가" 를 기록기의 IO 스코프 없이 바로 보기 위해서다. 실제 구현은 [BizLogFlagStateRecorder] 다.
 * 기록을 [FeatureFlagProvider.init] 안에서 부르는 것은 부르는 쪽이 잊어버릴 수 있어서다 —
 * 어떤 배정을 받았는지 모르면 실험 결과를 나중에 되짚을 수 없다.
 */
fun interface FlagStateRecorder {
    /**
     * @param assignments 플래그 이름 → 이 사용자가 받은 값. 되짚어 읽는 값이라 글자로 넘긴다.
     */
    fun record(environment: AppEnvironment, assignments: Map<String, String>)
}

/**
 * 기본 구현을 만든다.
 *
 * 구현 클래스를 내보내지 않는 것은 바깥이 [FeatureFlagProvider] 계약만 보게 하려는 것이다 —
 * 디스패처는 한정자(IoDispatcher)로 골라 받아야 하므로, [featureFlagModule] 이 이 함수를 감싼다.
 */
fun createFeatureFlagProvider(
    remote: RemoteConfigSource,
    environment: AppEnvironment,
    stateRecorder: FlagStateRecorder,
    ioDispatcher: CoroutineDispatcher,
): FeatureFlagProvider =
    DefaultFeatureFlagProvider(remote, environment, stateRecorder, ioDispatcher)
