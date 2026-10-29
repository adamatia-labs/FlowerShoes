package com.sadturtleman.flowershoes.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

/**
 * 기본 네트워크가 인터넷에 닿는지(VALIDATED). 와이파이에 붙었어도 인터넷이 안 되면 끊긴 것으로 본다 —
 * 그래야 동기화 실패를 서버 오류로 잘못 보이지 않는다.
 * 기본 네트워크가 바뀔 때(와이파이 → 모바일)는 onLost 없이 새 네트워크의 onCapabilitiesChanged 가 온다.
 */
@Composable
internal actual fun rememberOnline(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return produceState(initialValue = manager.activeNetwork?.let(manager::getNetworkCapabilities).online(), manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                value = capabilities.online()
            }

            override fun onLost(network: Network) {
                value = false
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitDispose { manager.unregisterNetworkCallback(callback) }
    }
}

private fun NetworkCapabilities?.online(): Boolean =
    this != null && hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

/** 스토어 등록 패키지(prod). dev · qa 플레이버도 실제 스토어 페이지로 보낸다. functions 의 PLAY_STORE_URL 과 같다. */
internal actual val storeUrl: String = "https://play.google.com/store/apps/details?id=com.sadturtleman.flowershoes"
