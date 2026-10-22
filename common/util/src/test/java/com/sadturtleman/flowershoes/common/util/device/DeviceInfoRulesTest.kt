package com.sadturtleman.flowershoes.common.util.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 읽어 온 값에서 끌어내는 판단들의 회귀 테스트.
 *
 * 수집 자체는 안드로이드 API 를 읽는 일이라 여기서 세울 수 없다. 대신 그 값을 가지고
 * 우리가 내리는 판단 — 에뮬레이터인가, 스토어를 거쳤는가, 이 주사율에서 프레임 예산은 얼마인가 —
 * 은 순수 코틀린이고, 틀려도 화면에 아무 표시가 나지 않는 쪽이라 여기서 못 박아 둔다.
 */
class DeviceInfoRulesTest {

    @Test
    fun `에뮬레이터 지문은 실기기와 구분된다`() {
        assertTrue(
            DeviceInfoRules.isProbablyEmulator(
                fingerprint = "generic/sdk_gphone64_arm64/emu64a:15/UE1A",
                brand = "google",
                model = "sdk_gphone64_arm64",
                hardware = "ranchu",
            ),
        )
        assertFalse(
            DeviceInfoRules.isProbablyEmulator(
                fingerprint = "samsung/dm3qksx/dm3q:15/AP3A",
                brand = "samsung",
                model = "SM-S911N",
                hardware = "qcom",
            ),
        )
    }

    @Test
    fun `스토어를 거치지 않은 설치는 사이드로드로 잡힌다`() {
        assertTrue(DeviceInfoRules.isSideloaded(null))
        assertTrue(DeviceInfoRules.isSideloaded("com.company.internal.distributor"))
        assertFalse(DeviceInfoRules.isSideloaded("com.android.vending"))
    }

    /** 120Hz 기기에 60Hz 기준을 쓰면 이미 끊긴 프레임이 정상으로 적힌다. */
    @Test
    fun `프레임 예산은 주사율을 따라간다`() {
        assertEquals(8.33f, DeviceInfoRules.frameBudgetMillis(120f), 0.01f)
        assertEquals(16.67f, DeviceInfoRules.frameBudgetMillis(60f), 0.01f)
    }

    /** 주사율을 못 읽었다고 0 으로 나누거나 0ms 예산을 주면 모든 프레임이 jank 가 된다. */
    @Test
    fun `주사율을 읽지 못하면 60Hz 로 본다`() {
        assertEquals(16.67f, DeviceInfoRules.frameBudgetMillis(0f), 0.01f)
    }

    /** "안 뜨겁다" 와 "물어볼 수 없었다" 는 다른 값이다. 둘 다 스로틀링은 아니다. */
    @Test
    fun `발열을 읽지 못한 것과 정상인 것은 둘 다 스로틀링이 아니다`() {
        assertFalse(ThermalStatus.UNKNOWN.isThrottling)
        assertFalse(ThermalStatus.NONE.isThrottling)
        assertFalse(ThermalStatus.LIGHT.isThrottling)
        assertTrue(ThermalStatus.MODERATE.isThrottling)
        assertTrue(ThermalStatus.CRITICAL.isThrottling)
    }

    @Test
    fun `힙 사용률은 상한이 0 이어도 터지지 않는다`() {
        val unknownHeap = memoryInfo(maxHeap = 0L, usedHeap = 0L)
        assertEquals(0f, unknownHeap.heapUsageRatio, 0.001f)
        assertEquals(0.25f, memoryInfo(maxHeap = 256L, usedHeap = 64L).heapUsageRatio, 0.001f)
    }

    private fun memoryInfo(maxHeap: Long, usedHeap: Long) = MemoryInfo(
        availableBytes = 2_000_000_000L,
        totalBytes = 8_000_000_000L,
        thresholdBytes = 200_000_000L,
        isLowMemory = false,
        maxHeapBytes = maxHeap,
        usedHeapBytes = usedHeap,
    )
}
