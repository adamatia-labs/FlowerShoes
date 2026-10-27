package com.sadturtleman.flowershoes.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

data class BackupAttachmentRow(val id: String, val kind: String, val sha256: String)
data class BackupDatabaseSnapshot(val attachments: List<BackupAttachmentRow>, val letters: Int)

/** The writer stays reserved until references are captured, so later writes cannot split the snapshot. */
suspend fun AppDatabase.writeBackupSnapshot(path: String) = useWriterConnection { connection ->
    connection.usePrepared("VACUUM INTO ?") { it.bindText(1, path); it.step() }
    val attachments = connection.usePrepared("SELECT id, kind, sha256 FROM attachment") {
        buildList { while (it.step()) add(BackupAttachmentRow(it.getText(0), it.getText(1), it.getText(2))) }
    }
    val letters = connection.usePrepared("SELECT COUNT(*) FROM letter WHERE deletedAtMs IS NULL") {
        it.step(); it.getLong(0).toInt()
    }
    BackupDatabaseSnapshot(attachments, letters)
}

/**
 * Replace records through the existing Room connection so its observers remain valid on iOS.
 * The caller first migrates the temporary backup with Room. A malformed or incompatible
 * snapshot is rejected before deleting any records; SQL failures roll back the whole import.
 */
suspend fun AppDatabase.restoreBackupSnapshot(path: String) = useWriterConnection { connection ->
    connection.usePrepared("ATTACH DATABASE ? AS restored") { it.bindText(1, path); it.step() }
    try {
        connection.usePrepared("PRAGMA restored.integrity_check") {
            check(it.step() && it.getText(0) == "ok") { "백업 DB가 손상됐어요" }
        }
        connection.usePrepared("PRAGMA restored.user_version") {
            check(it.step() && it.getLong(0) == AppDatabase.VERSION.toLong()) { "백업 DB 버전이 맞지 않아요" }
        }
        val tables = connection.usePrepared("SELECT name FROM main.sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'") {
            buildList { while (it.step()) add(it.getText(0)) }
        }
        fun quote(name: String) = "\"${name.replace("\"", "\"\"")}\""
        val columns = tables.associateWith { table ->
            val current = connection.usePrepared("PRAGMA main.table_info(${quote(table)})") {
                buildList { while (it.step()) add(it.getText(1)) }
            }
            val restored = connection.usePrepared("PRAGMA restored.table_info(${quote(table)})") {
                buildList { while (it.step()) add(it.getText(1)) }
            }
            check(current == restored) { "백업 표가 맞지 않아요: $table" }
            current.joinToString(",", transform = ::quote)
        }
        connection.immediateTransaction {
            tables.forEach { table ->
                usePrepared("DELETE FROM main.${quote(table)}") { it.step() }
                usePrepared("INSERT INTO main.${quote(table)} (${columns.getValue(table)}) SELECT ${columns.getValue(table)} FROM restored.${quote(table)}") { it.step() }
            }
        }
    } finally {
        connection.usePrepared("DETACH DATABASE restored") { it.step() }
    }
}
