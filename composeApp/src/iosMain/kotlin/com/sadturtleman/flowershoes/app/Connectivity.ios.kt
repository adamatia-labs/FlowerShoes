@file:OptIn(ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.app

import kotlinx.cinterop.ExperimentalForeignApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import platform.Foundation.NSBundle
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/** NWPathMonitor 가 경로가 바뀔 때마다 알려 준다(시작하자마자 한 번). */
@Composable
internal actual fun rememberOnline(): State<Boolean> = produceState(initialValue = true) {
    val monitor = nw_path_monitor_create()
    nw_path_monitor_set_update_handler(monitor) { path -> value = nw_path_get_status(path) == nw_path_status_satisfied }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
    awaitDispose { nw_path_monitor_cancel(monitor) }
}

/** App Store ID 는 Info.plist 의 KkotsinAppStoreId(Config.xcconfig 의 APP_STORE_ID). functions 의 APP_STORE_URL 과 같은 자리표시다. */
internal actual val storeUrl: String
    get() {
        val id = (NSBundle.mainBundle.objectForInfoDictionaryKey("KkotsinAppStoreId") as? String)?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("$(") } ?: "TODO_APP_STORE_ID"
        return "https://apps.apple.com/app/id$id"
    }
