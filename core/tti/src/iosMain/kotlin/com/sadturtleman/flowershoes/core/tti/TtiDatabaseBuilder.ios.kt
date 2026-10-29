package com.sadturtleman.flowershoes.core.tti

import androidx.room.Room
import androidx.room.RoomDatabase
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Application Support/db 폴더 — 앱 DB 와 같은 자리다(:core:database 의 databaseBuilder 에 이유를 적어 두었다).
 * 계측 기록이라 백업에서도 뺀다.
 */
@OptIn(ExperimentalForeignApi::class)
fun ttiDatabaseBuilder(): RoomDatabase.Builder<TtiDatabase> {
    val fm = NSFileManager.defaultManager
    val support = requireNotNull(
        fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null),
    ) { "Application Support 폴더를 열 수 없다" }
    val dir = requireNotNull(support.URLByAppendingPathComponent("db", isDirectory = true))
    fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    val file = requireNotNull(dir.URLByAppendingPathComponent(TtiDatabase.FILE_NAME)?.path)
    return Room.databaseBuilder<TtiDatabase>(name = file)
}
