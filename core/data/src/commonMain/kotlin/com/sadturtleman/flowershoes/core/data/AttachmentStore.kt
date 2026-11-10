package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.AttachmentEntity
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlin.uuid.Uuid
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

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
        require(isSafeId(id)) { "첨부 식별자가 올바르지 않습니다" }
        fs.createDirectories(root)
        val path = root / id
        val metadata = fs.metadataOrNull(path)
        require(metadata?.symlinkTarget == null && (metadata == null || safeExistingPath(root, path.toString(), fs) != null)) { "첨부 경로가 올바르지 않습니다" }
        fs.write(path) { write(bytes) }
        db.attachmentDao().upsert(
            AttachmentEntity(id, kind.name, path.toString(), bytes.toByteString().sha256().hex(), bytes.size.toLong()),
        )
        return id
    }

    /** 연결 해제 "모두 지우기" · 탈퇴. */
    fun deleteAllFiles() {
        if (fs.exists(root)) fs.deleteRecursively(root)
    }

    suspend fun read(id: String): ByteArray? {
        if (!isSafeId(id)) return null
        val row = db.attachmentDao().get(id) ?: return null
        if (!isSafeId(row.id)) return null
        val path = root / row.id
        if (fs.metadataOrNull(path)?.symlinkTarget != null) return null
        val actual = safeExistingPath(root, path.toString(), fs) ?: return null
        return fs.read(actual) { readByteArray() }
    }

    companion object {
        /** 기존 안전한 이름은 유지하되 어느 플랫폼에서도 단일 경로 구성 요소만 허용한다. */
        fun isSafeId(id: String): Boolean =
            id.isNotEmpty() && id != "." && id != ".." && id.encodeToByteArray().size <= 255 &&
                id.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

        /** 예전 위젯 스냅샷의 경로도 읽기 전에 첨부 폴더의 직접 자식인지 확인한다. */
        fun safePath(root: Path, raw: String?): Path? {
            val path = raw?.let { runCatching { it.toPath() }.getOrNull() } ?: return null
            return path.takeIf { isSafeId(it.name) && it == root / it.name }
        }

        /** 기존 스냅샷에서 읽는 파일은 심볼릭 링크까지 확인한다. */
        fun safeExistingPath(root: Path, raw: String?, fs: FileSystem = FileSystem.SYSTEM): Path? {
            val path = safePath(root, raw) ?: return null
            return runCatching { fs.canonicalize(path).takeIf { it.parent == fs.canonicalize(root) } }.getOrNull()
        }
    }
}
