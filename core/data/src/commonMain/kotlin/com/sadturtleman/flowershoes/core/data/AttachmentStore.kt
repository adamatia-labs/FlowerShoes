package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.AttachmentEntity
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlin.uuid.Uuid
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path

/**
 * 첨부 파일(그림 문서 · PNG · 사진)을 앱 전용 폴더에 둔다. 행(attachment)에는 경로와 sha256 만 적는다.
 *
 * 상대에게 보낼 때는 무작위 키로 암호화해 Storage 에 올린다(:core:sync). 여기 있는 것은 평문 원본이다.
 * 폴더는 OS 백업에서 빠진다(Android files/ 은 자동 백업 규칙, iOS 는 Application Support 제외 속성).
 */
class AttachmentStore(
    private val db: AppDatabase,
    private val root: Path,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    suspend fun put(kind: AttachmentKind, bytes: ByteArray): String = putWithId(Uuid.random().toString(), kind, bytes)

    /** 받은 첨부를 보낸 쪽과 같은 id 로 둔다(편지 블록이 그 id 를 가리킨다). */
    suspend fun putWithId(id: String, kind: AttachmentKind, bytes: ByteArray): String {
        fs.createDirectories(root)
        val path = root / id
        fs.write(path) { write(bytes) }
        db.attachmentDao().upsert(
            AttachmentEntity(id, kind.name, path.toString(), bytes.toByteString().sha256().hex(), bytes.size.toLong()),
        )
        return id
    }

    suspend fun read(id: String): ByteArray? {
        val row = db.attachmentDao().get(id) ?: return null
        val path = root / row.id
        return if (fs.exists(path)) fs.read(path) { readByteArray() } else null
    }
}
