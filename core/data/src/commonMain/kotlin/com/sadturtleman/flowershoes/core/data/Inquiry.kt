package com.sadturtleman.flowershoes.core.data

import kotlinx.coroutines.flow.Flow

/** 문의 유형(설정 › 문의하기). 서버 규칙(firestore.rules)과 글자까지 같다. */
val INQUIRY_TYPES = listOf("오류 신고", "연결 · 백업", "계정 · 탈퇴", "제안", "기타")

/** 문의 한 건(문의 내역). [answer] 가 있으면 답변 완료. */
data class Inquiry(
    val id: String,
    val type: String,
    val title: String,
    val createdAtMs: Long,
    val answer: String? = null,
    val answeredAtMs: Long? = null,
)

/**
 * 문의 보내기 · 내 문의 보기(users/{uid}/inquiries). 회사가 읽어야 하는 내용이라 커플 키로 암호화하지 않는다.
 * 스크린샷은 JPEG 바이트(최대 3장).
 */
interface InquiryTransport {
    suspend fun submit(type: String, body: String, email: String, device: String?, screenshots: List<ByteArray>)

    fun observeMine(): Flow<List<Inquiry>>

    /** 답변 받을 이메일 기본값(로그인 계정 이메일). */
    fun accountEmail(): String?
}
