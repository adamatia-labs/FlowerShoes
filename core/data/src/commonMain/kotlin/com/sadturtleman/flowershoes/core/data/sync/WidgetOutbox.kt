package com.sadturtleman.flowershoes.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.io.encoding.Base64

@Serializable
private data class WidgetOutgoing(val coupleId: String, val fromDeviceId: String, val opId: String, val kind: String, val header: String, val ct: String, val sig: String)

@Serializable
private data class WidgetJournal(val pending: WidgetJournalEnvelope? = null)

@Serializable
private data class WidgetJournalEnvelope(val opId: String, val body: String)

/** Import the durable journal even if the extension stopped before publishing its outbox file. */
suspend fun importWidgetOutbox(sync: SyncClient, root: Path, fs: FileSystem = FileSystem.SYSTEM) {
    val json = Json { ignoreUnknownKeys = true }
    suspend fun import(body: String): Boolean {
        val q = json.decodeFromString(WidgetOutgoing.serializer(), body)
        return sync.importWidgetOp(q.coupleId, MailItem(q.opId, q.kind, q.fromDeviceId,
            Base64.decode(q.header), Base64.decode(q.ct), Base64.decode(q.sig)))
    }
    suspend fun safely(block: suspend () -> Boolean): Boolean = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
    val journal = root / "widget-send-journal.json"
    if (fs.exists(journal)) safely {
        val pending = json.decodeFromString(WidgetJournal.serializer(), fs.read(journal) { readUtf8() }).pending
        pending != null && import(Base64.decode(pending.body).decodeToString())
        // The extension alone updates the journal under its process lock. Reimport is idempotent.
    }
    val directory = root / "widget-outbox"
    if (!fs.exists(directory)) return
    fs.list(directory).filter { it.name.endsWith(".json") }.forEach { file ->
        if (safely { import(fs.read(file) { readUtf8() }) }) {
            fs.delete(file)
            fs.delete(file.parent!! / "${file.name}.delivered", mustExist = false)
        }
    }
}
