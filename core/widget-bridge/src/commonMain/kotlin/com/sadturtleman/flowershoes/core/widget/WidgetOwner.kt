package com.sadturtleman.flowershoes.core.widget

import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import kotlinx.serialization.Serializable

/** 앱과 iOS 확장이 같은 관계의 스냅샷만 수정하도록 전달하는 소유권. 비밀 키는 포함하지 않는다. */
@Serializable
data class WidgetOwner(
    val userId: String,
    val deviceId: String,
    val accountSession: String,
    val coupleId: String,
    val partnerUserId: String,
    val keyEpoch: Int,
    val linkedAtMs: Long,
) {
    companion object {
        fun from(account: DeviceState, couple: CoupleEntity?): WidgetOwner? {
            if (couple == null || couple.myUserId != account.userId || couple.partnerUserId.isBlank()) return null
            return WidgetOwner(account.userId, account.deviceId, account.accountSession,
                couple.id, couple.partnerUserId, couple.keyEpoch, couple.linkedAtMs)
        }
    }
}
