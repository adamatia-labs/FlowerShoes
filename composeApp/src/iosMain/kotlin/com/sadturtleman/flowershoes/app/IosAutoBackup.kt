package com.sadturtleman.flowershoes.app

import com.sadturtleman.flowershoes.core.backup.AutoBackupPolicy
import com.sadturtleman.flowershoes.core.backup.BackupManager
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform
import kotlin.time.Clock

private val backupScope = MainScope()
private var backupJob: Job? = null

/** Swift holds a finite UIKit background task; expiration cancels this upload safely. */
fun onBackgroundBackup(connected: Boolean, wifi: Boolean, done: () -> Unit) {
    if (backupJob?.isActive == true) { done(); return }
    backupJob = backupScope.launch {
        try {
            withTimeout(25_000) {
                val koin = KoinPlatform.getKoinOrNull() ?: return@withTimeout
                val settings = koin.get<DeviceSettings>()
                val state = settings.state.first()
                val enabled = settings.autoBackup.first()
                val allowCellular = settings.cellularBackup.first()
                if (!state.onboarded || !AutoBackupPolicy.shouldRun(enabled, connected, wifi, allowCellular, null, Clock.System.now().toEpochMilliseconds())) return@withTimeout
                val manager = koin.getOrNull<BackupManager>() ?: return@withTimeout
                val last = manager.lastBackupAt.value ?: manager.preview(state.userId)?.createdAtMs
                if (AutoBackupPolicy.shouldRun(enabled, connected, wifi, allowCellular, last, Clock.System.now().toEpochMilliseconds())) manager.backupNow(state.userId)
            }
        } catch (_: Exception) {
            // The previous committed manifest remains available; retry next eligible background.
        } finally { done() }
    }
}

fun cancelBackgroundBackup() { backupJob?.cancel() }
