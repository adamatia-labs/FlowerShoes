package com.sadturtleman.flowershoes.core.backup

/** Same 30-minute foreground-to-background policy on both phones; OS time remains best effort. */
object AutoBackupPolicy {
    fun shouldRun(enabled: Boolean, connected: Boolean, wifi: Boolean, allowCellular: Boolean, lastAtMs: Long?, nowMs: Long): Boolean =
        enabled && connected && (wifi || allowCellular) && (lastAtMs == null || nowMs - lastAtMs >= 30 * 60 * 1000L)
}
