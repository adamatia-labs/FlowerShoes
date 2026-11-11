package com.sadturtleman.flowershoes.core.widget

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

@Serializable
private data class PublishedWidgetOwner(val snapshotOwner: WidgetOwner? = null)

private val linkJson = Json { ignoreUnknownKeys = true }

/** 정리 경계와 연결 정보 발행기가 함께 사용하는 무효화. 파일 실패는 호출자가 저널로 재시도한다. */
fun invalidateWidgetFiles(root: Path, fs: FileSystem = FileSystem.SYSTEM) {
    SnapshotStore(root / "widget/snapshot.json", fs).clear()
    fs.delete(root / "link.json", mustExist = false)
}

/** 실제 계정을 잠근 뒤 현재 관계를 조회한다. 다른 소유자의 관계는 이전 링크를 되살리지 않는다. */
suspend fun publishWidgetLink(
    db: AppDatabase,
    settings: DeviceSettings,
    root: Path,
    fs: FileSystem = FileSystem.SYSTEM,
    build: suspend (DeviceState, CoupleEntity) -> String?,
) {
    val account = settings.state.first()
    settings.withCurrentAccount(account) {
        db.useWriterConnection { tx -> tx.immediateTransaction {
            if (db.cleanupDao().get() != null) return@immediateTransaction
            val couple = db.coupleDao().get()
            val owner = WidgetOwner.from(account, couple)
            SnapshotStore(root / "widget/snapshot.json", fs).clearIfOwnerChanged(owner)
            val link = root / "link.json"
            val previousOwner = runCatching {
                linkJson.decodeFromString(PublishedWidgetOwner.serializer(), fs.read(link) { readUtf8() }).snapshotOwner
            }.getOrNull()
            // 새 키 미러를 준비하는 동안에도 이전 계정의 링크를 사용할 수 없어야 한다.
            if (owner == null || previousOwner != owner) fs.delete(link, mustExist = false)
            val content = try {
                if (owner != null) build(account, checkNotNull(couple)) else null
            } catch (failure: Throwable) {
                fs.delete(link, mustExist = false)
                throw failure
            }
            if (content == null) {
                fs.delete(link, mustExist = false)
            } else {
                fs.createDirectories(root)
                val temporary = root / "link.json.tmp"
                fs.write(temporary) { writeUtf8(content) }
                fs.atomicMove(temporary, link)
            }
        } }
    }
}
