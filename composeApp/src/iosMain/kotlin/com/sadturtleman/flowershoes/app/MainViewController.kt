package com.sadturtleman.flowershoes.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.databaseBuilder
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

private var started = false

/** iosApp(SwiftUI)이 부르는 진입점. Koin 은 한 번만 시작한다. */
@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    if (!started) {
        val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val settingsPath = requireNotNull(support?.path) + "/kkotsin_device.preferences_pb"
        startKoin {
            modules(
                kkotsinModules(
                    database = databaseBuilder().buildKkotsin(),
                    settingsStore = PreferenceDataStoreFactory.createWithPath { settingsPath.toPath() },
                ),
            )
        }
        started = true
    }
    return ComposeUIViewController { KkotsinApp() }
}
