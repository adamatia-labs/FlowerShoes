package com.sadturtleman.flowershoes.common.util.device

/**
 * [DeviceInfoProvider] 가 내보내는 값 타입들.
 *
 * 대부분의 항목은 [Int] · [String] 하나로 끝난다. 여기 모인 것은 그렇게 둘 수 없는 것들이다 —
 * 한 번의 조회에서 함께 나오거나(메모리 · 창 크기), 안드로이드의 정수 상수를 그대로 내보내면
 * 부르는 쪽이 `PowerManager.THERMAL_STATUS_*` 를 알아야 하는 것들(발열 · 연결 타입)이다.
 */

/** 창의 픽셀 크기. 화면이 아니라 우리 창이다. */
data class WindowSize(
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * 메모리. 기기 전체와 우리 힙을 함께 본다.
 *
 * 둘이 따로 노는 경우가 흔하다 — 힙은 넉넉한데 기기 전체가 바닥이면 남이 쓴 것이고,
 * 반대면 우리 누수다. 한쪽만 들고 있으면 이 구분을 못 한다.
 *
 * @param availableBytes 기기 전체에서 남은 양(`MemoryInfo.availMem`).
 * @param totalBytes 기기 전체 RAM(`MemoryInfo.totalMem`).
 * @param thresholdBytes 이 아래로 내려가면 OS 가 프로세스를 죽이기 시작하는 선(`MemoryInfo.threshold`).
 * @param isLowMemory OS 가 이미 메모리 부족으로 보고 있는가(`MemoryInfo.lowMemory`).
 * @param maxHeapBytes 우리 프로세스가 쓸 수 있는 힙 상한(`Runtime.maxMemory`).
 * @param usedHeapBytes 그중 지금 쓰고 있는 양.
 */
data class MemoryInfo(
    val availableBytes: Long,
    val totalBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val maxHeapBytes: Long,
    val usedHeapBytes: Long,
) {

    /** 힙 사용률. 1.0 에 가까울수록 OutOfMemory 가 가깝다. */
    val heapUsageRatio: Float
        get() = if (maxHeapBytes == 0L) 0f else usedHeapBytes.toFloat() / maxHeapBytes

    /** 기기 전체 사용률. */
    val deviceUsageRatio: Float
        get() = if (totalBytes == 0L) 0f else (totalBytes - availableBytes).toFloat() / totalBytes
}

/** 화면 방향. 알 수 없는 경우까지 세 가지다. */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
    UNDEFINED,
}

/**
 * 발열 단계(`PowerManager.getCurrentThermalStatus`).
 *
 * [MODERATE] 부터는 OS 가 이미 클럭을 내리고 있다. 그 구간에서 느려진 것을 우리 퇴행으로 읽으면
 * 있지도 않은 원인을 찾게 된다.
 */
enum class ThermalStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,

    /** API 29 아래라 물어볼 수 없었다. "괜찮다" 와 다르다. */
    UNKNOWN,
    ;

    /** 이 단계에서 이미 성능이 눌리고 있는가. */
    val isThrottling: Boolean
        get() = this == MODERATE || this == SEVERE || this == CRITICAL ||
            this == EMERGENCY || this == SHUTDOWN
}

/** 연결 타입. `NetworkCapabilities` 의 transport 를 우리 말로 옮긴 것이다. */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    VPN,

    /** 붙어는 있는데 위 어느 것도 아니다. */
    OTHER,

    /** 끊겨 있다. 권한이 없어 볼 수 없는 경우도 여기로 온다. */
    NONE,
    ;

    val isConnected: Boolean
        get() = this != NONE
}
