package com.sadturtleman.flowershoes.common.util.device.reader

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.sadturtleman.flowershoes.common.util.device.MemoryInfo
import com.sadturtleman.flowershoes.common.util.device.NetworkType
import com.sadturtleman.flowershoes.common.util.device.ThermalStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 순간의 기기 사정을 읽는 곳. 이벤트 한 건에 붙일 값들이라 역시 캐시하지 않는다.
 *
 * 시스템 서비스는 언제든 null 일 수 있고(그런 하드웨어가 없는 기기가 있다),
 * 네트워크 조회는 권한이 없으면 던진다. 계측이 앱을 죽이는 일은 없어야 하므로
 * 못 읽은 값은 "모른다" 로 떨어뜨린다 — 0 이나 false 로 슬쩍 채우지 않는다.
 */
internal class DeviceStateReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun memoryInfo(): MemoryInfo {
        val deviceMemory = ActivityManager.MemoryInfo().also { info ->
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        }
        val runtime = Runtime.getRuntime()
        return MemoryInfo(
            availableBytes = deviceMemory.availMem,
            totalBytes = deviceMemory.totalMem,
            thresholdBytes = deviceMemory.threshold,
            isLowMemory = deviceMemory.lowMemory,
            maxHeapBytes = runtime.maxMemory(),
            // totalMemory 는 지금까지 확보한 힙이고 freeMemory 는 그중 노는 부분이다.
            // 둘의 차가 실제로 쓰고 있는 양이다.
            usedHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
        )
    }

    fun isLowRamDevice(): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: false

    fun cpuCoreCount(): Int = Runtime.getRuntime().availableProcessors()

    /** 잔량을 못 읽으면 [BatteryManager] 가 [Int.MIN_VALUE] 같은 값을 준다. 범위 밖이면 -1 로 접는다. */
    fun batteryPercent(): Int {
        val capacity = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: return UNKNOWN_BATTERY
        return if (capacity in 0..100) capacity else UNKNOWN_BATTERY
    }

    fun isCharging(): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging ?: false

    fun isPowerSaveMode(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: false

    /**
     * 발열 단계. API 29 부터만 물어볼 수 있다.
     *
     * 그 아래를 [ThermalStatus.NONE] 이 아니라 [ThermalStatus.UNKNOWN] 으로 두는 것이 중요하다 —
     * "안 뜨겁다" 와 "물어볼 수 없었다" 를 같은 값으로 적으면, 구버전 기기의 스로틀링이
     * 전부 정상 표본으로 섞여 들어간다.
     */
    fun thermalStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
        val powerManager = context.getSystemService(PowerManager::class.java)
            ?: return ThermalStatus.UNKNOWN
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
            else -> ThermalStatus.UNKNOWN
        }
    }

    fun networkType(): NetworkType = capabilities()?.let { capabilities ->
        // VPN 을 먼저 본다. VPN 위의 트래픽은 실제 회선이 무엇이든 그 터널을 지나므로,
        // 지연을 볼 때 알아야 할 것은 아래 회선이 아니라 터널이 끼어 있다는 사실이다.
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            else -> NetworkType.OTHER
        }
    } ?: NetworkType.NONE

    fun isMeteredNetwork(): Boolean = runCatching {
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered
    }.getOrNull() ?: false

    fun downstreamBandwidthKbps(): Int = capabilities()?.linkDownstreamBandwidthKbps ?: 0

    fun upstreamBandwidthKbps(): Int = capabilities()?.linkUpstreamBandwidthKbps ?: 0

    /**
     * 지금 붙어 있는 망의 능력. 끊겨 있거나 읽을 수 없으면 null 이다.
     *
     * ACCESS_NETWORK_STATE 는 이 모듈의 매니페스트가 선언하므로 앱에 늘 있다. 그래도 감싸는 것은
     * 일부 기기에서 이 조회가 던지는 사례가 있기 때문이다 — 네트워크를 못 읽어 앱이 죽는 것보다
     * "모른다" 로 적히는 편이 낫다.
     */
    private fun capabilities(): NetworkCapabilities? = runCatching {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching null
        connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
    }.getOrNull()

    private companion object {
        const val UNKNOWN_BATTERY = -1
    }
}
