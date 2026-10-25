package com.sadturtleman.flowershoes.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Application Support/db 폴더. Documents 는 iCloud 백업 · 기기 간 이전에 기본 포함되므로 쓰지 않고,
 * App Group 도 쓰지 않는다(잠금을 잡은 채 백그라운드로 가면 0xdead10cc 로 강제 종료된다).
 * 폴더째 백업에서 뺀다 — 파일이 아직 없어도 그 안에 생기는 DB · WAL 이 모두 빠진다.
 */
@OptIn(ExperimentalForeignApi::class)
fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val fm = NSFileManager.defaultManager
    val support = requireNotNull(
        fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null),
    ) { "Application Support 폴더를 열 수 없다" }
    val dir = requireNotNull(support.URLByAppendingPathComponent("db", isDirectory = true))
    fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    val file = requireNotNull(dir.URLByAppendingPathComponent(AppDatabase.FILE_NAME)?.path)
    return Room.databaseBuilder<AppDatabase>(name = file)
}

/** Used only for a private, temporary restore copy, allowing Room's normal migrations. */
fun backupDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder<AppDatabase>(name = path)
