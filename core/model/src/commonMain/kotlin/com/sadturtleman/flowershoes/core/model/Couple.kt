package com.sadturtleman.flowershoes.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** 곰신(기다리는 쪽) 또는 군인. 온보딩 첫 단계에서 고르고, 테마 기본값과 군 정보 우선순위가 여기서 갈린다. */
@Serializable
enum class Role { GOMSIN, SOLDIER }

/** 이 기기에만 적용되는 강조색 묶음. 동기화하지 않는다. */
@Serializable
enum class ThemeAccent {
    ROSE, OLIVE, NAVY, MONO;

    companion object {
        /** 온보딩 테마 단계의 기본 선택. 곰신은 로즈, 군인은 올리브. */
        fun defaultFor(role: Role): ThemeAccent = when (role) {
            Role.GOMSIN -> ROSE
            Role.SOLDIER -> OLIVE
        }
    }
}

@Serializable
data class User(
    val id: String,
    val role: Role,
    val nickname: String,
    val nicknameLamport: Long = 0,
)

/**
 * 연결된 커플 한 쌍.
 *
 * [keyEpoch] 는 지금 쓰는 커플 키 번호다. 기기가 바뀔 때마다 1씩 오른다.
 * [anniversary] 는 둘 다 고칠 수 있는 값이라 필드 단위 lamport 를 따로 둔다.
 */
@Serializable
data class Couple(
    val id: String,
    val myUserId: String,
    val partnerUserId: String,
    val keyEpoch: Int,
    val linkedAt: Instant,
    val anniversary: kotlinx.datetime.LocalDate? = null,
    val anniversaryLamport: Long = 0,
)

/**
 * 기기 하나. 한 사람당 active 기기는 1대다.
 *
 * 새 기기는 [DeviceState.PENDING] 으로 등록되고, 상대 기기가 커플 키로 서명한 승인을 확인한 뒤에야
 * [DeviceState.ACTIVE] 가 된다. 로그인만으로는 진짜 폰을 끊을 수 없게 하기 위해서다.
 */
@Serializable
data class Device(
    val id: String,
    val userId: String,
    val uid: String,
    val signPub: ByteArray,
    val boxPub: ByteArray,
    val state: DeviceState,
    val registeredAt: Instant,
    val revokedAt: Instant? = null,
) {
    override fun equals(other: Any?): Boolean = other is Device && other.id == id && other.state == state
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
enum class DeviceState { PENDING, ACTIVE, REVOKED }
