package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotifyMasterTest {

    @Test
    fun `큰 스위치를 끄면 편지 · 보고싶어 · 일정 알림이 모두 꺼지고 3개월 기록 알림은 그대로다`() {
        val off = NotificationPrefs().withNotifyMaster(false)
        assertEquals(
            NotificationPrefs(letters = false, missYou = false, status = false, eventEve = false, anniversary = false, exportReminder = true),
            off,
        )
        assertFalse(off.notifyMasterOn)
    }

    @Test
    fun `하나라도 켜져 있으면 큰 스위치는 켜짐이다`() {
        val prefs = NotificationPrefs().withNotifyMaster(false).copy(letters = true)
        assertTrue(prefs.notifyMasterOn)
    }

    @Test
    fun `큰 스위치를 다시 켜면 모두 켜진다`() {
        val on = NotificationPrefs(letters = false, missYou = false, status = false, eventEve = false, anniversary = false).withNotifyMaster(true)
        assertEquals(NotificationPrefs(), on)
    }
}
