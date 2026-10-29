package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.model.BookCover
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.MissYou
import com.sadturtleman.flowershoes.core.model.Receipt
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.Status
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Op 본문(암호화 전 평문). 커플 키로 암호화되어 우편함에 올라간다.
 *
 * 받는 앱이 모르는 종류(새 버전이 추가한 것)는 [OpCodec.decode] 가 null 을 돌려주고,
 * 커서는 그대로 전진한다 — 모르는 Op 하나 때문에 그 뒤가 막히지 않게.
 */
@Serializable
sealed interface OpPayload {

    @Serializable @SerialName("missyou")
    data class MissYouPut(val missYou: MissYou) : OpPayload

    @Serializable @SerialName("status")
    data class StatusPut(val status: Status) : OpPayload

    /** 일정. 만남 사진이 있으면 [photos] 를 보낼 때 채운다(편지 사진과 같은 blob). */
    @Serializable @SerialName("event")
    data class EventPut(val event: Event, val photos: List<PhotoRef> = emptyList()) : OpPayload

    /** 받은 휴가(휴가 관리 › 출처 내역). 조용한 종류(PROFILE)로 보낸다 — 알림 없이 반영. */
    @Serializable @SerialName("leave")
    data class LeaveGrantPut(val grant: LeaveGrant) : OpPayload

    /** 편지 한 통 = Op 하나. 그림 메타와 첨부 참조(키 포함)를 함께 싣는다. [photos] 는 보낼 때 사진 블록에서 채운다. */
    @Serializable @SerialName("letter")
    data class LetterPut(val letter: Letter, val drawings: List<DrawingMeta> = emptyList(), val photos: List<PhotoRef> = emptyList()) : OpPayload

    @Serializable @SerialName("receipt")
    data class ReceiptPut(val receipt: Receipt) : OpPayload

    @Serializable @SerialName("service")
    data class ServicePut(val service: Service) : OpPayload

    /** 연결 직후 서로의 역할 · 닉네임을 알린다(7-1-1 정보 맞추기에 필요). */
    @Serializable @SerialName("user")
    data class UserPut(
        val userId: String,
        val role: String,
        val nickname: String,
        val lamport: Long,
        val deviceName: String = "",
        /** 산 것(lifetime · book). 상대 폰이 함께 쓴다. */
        val perks: List<String> = emptyList(),
    ) : OpPayload

    /** 추억 책 표지(두 사람 공유). 조용한 종류(PROFILE)로 보낸다. 행 LWW(lamport, writerDevice). */
    @Serializable @SerialName("bookcover")
    data class BookCoverPut(val cover: BookCover, val lamport: Long, val writerDevice: String) : OpPayload

    @Serializable @SerialName("delete")
    data class Delete(val entity: DeletableEntity, val id: String, val lamport: Long, val atMs: Long, val writerDevice: String) : OpPayload

    /**
     * 스냅샷. 상대가 이미 지운 Op 을 달라고 했거나(floor 아래), 기기를 바꾼 상대에게 빠진 부분을 메울 때.
     * [coversThrough] 까지 반영한 것으로 처리하라는 뜻이다.
     */
    @Serializable @SerialName("snapshot")
    data class Snapshot(val coversThrough: Long, val items: List<OpPayload>) : OpPayload
}

@Serializable
enum class DeletableEntity { EVENT, LETTER, LEAVE_GRANT }

/** 편지 안 사진(편지 쓰기 › 사진). 그림 문서처럼 무작위 키로 암호화한 blob 으로 따로 간다. */
@Serializable
data class PhotoRef(val attachmentId: String, val blobId: String? = null, val blobKey: String? = null)

/** 편지 안 그림의 메타. 문서 · PNG 본문은 Storage 첨부로 따로 간다. */
@Serializable
data class DrawingMeta(
    val id: String,
    val orderInLetter: Int,
    val shape: String,
    val docAttachmentId: String?,
    val pngAttachmentId: String,
    val caption: String?,
    val replayEnabled: Boolean,
    val replayDurationMs: Long,
    /** 보낼 때 채운다: 그림 문서를 무작위 키로 암호화해 올린 blob 과 그 키(base64). Op 본문 자체가 커플 키로 암호화된다. */
    val docBlobId: String? = null,
    val docBlobKey: String? = null,
    /** 완성 PNG 도 같은 방식으로(위젯 · 알림에 바로 쓴다). */
    val pngBlobId: String? = null,
    val pngBlobKey: String? = null,
    /** 받는 폰 위젯에 띄울지(그림 보내기 › 위젯에 띄우기). 예전 앱이 보낸 그림은 띄운다. */
    val showOnWidget: Boolean = true,
    /** 배경 사진(그림 / 사진). 문서의 PhotoBackground 가 이 첨부 id 를 가리킨다. 그림 문서처럼 blob 으로 따로 간다. */
    val photoAttachmentId: String? = null,
    val photoBlobId: String? = null,
    val photoBlobKey: String? = null,
)

object OpCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "t"
    }

    fun encode(payload: OpPayload): ByteArray = json.encodeToString(OpPayload.serializer(), payload).encodeToByteArray()

    /** 모르는 종류 · 깨진 본문이면 null. */
    fun decode(bytes: ByteArray): OpPayload? = try {
        json.decodeFromString(OpPayload.serializer(), bytes.decodeToString())
    } catch (_: Exception) {
        null
    }
}
