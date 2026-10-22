package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.model.Event
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

    @Serializable @SerialName("event")
    data class EventPut(val event: Event) : OpPayload

    /** 편지 한 통 = Op 하나. 그림 메타와 첨부 참조(키 포함)를 함께 싣는다. */
    @Serializable @SerialName("letter")
    data class LetterPut(val letter: Letter, val drawings: List<DrawingMeta> = emptyList()) : OpPayload

    @Serializable @SerialName("receipt")
    data class ReceiptPut(val receipt: Receipt) : OpPayload

    @Serializable @SerialName("service")
    data class ServicePut(val service: Service) : OpPayload

    /** 연결 직후 서로의 역할 · 닉네임을 알린다(7-1-1 정보 맞추기에 필요). */
    @Serializable @SerialName("user")
    data class UserPut(val userId: String, val role: String, val nickname: String, val lamport: Long) : OpPayload

    @Serializable @SerialName("anniversary")
    data class AnniversaryPut(val date: String?, val lamport: Long) : OpPayload

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
enum class DeletableEntity { EVENT, LETTER }

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
