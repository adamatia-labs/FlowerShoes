package com.sadturtleman.flowershoes.common.network

/**
 * 서버가 HTTP 200 으로 응답했지만 본문으로 실패를 알린 경우.
 *
 * HTTP 오류는 Retrofit 이 [retrofit2.HttpException] 으로 던지므로 여기서 다루지 않는다.
 * 본문 실패 규칙(결과 코드 필드 이름 등)은 서버 스펙이 정해지면 응답 껍데기 옆에 함께 둔다.
 */
class ApiException(
    val code: String,
    override val message: String,
) : RuntimeException("[$code] $message")
