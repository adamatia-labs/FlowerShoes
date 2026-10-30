package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class SettingsLabelsTest {

    @Test
    fun `함께한 날은 N일째, 정하지 않았으면 정하기`() {
        assertEquals("411일째", daysTogetherLabel(411))
        assertEquals("정하기", daysTogetherLabel(null))
    }

    @Test
    fun `군 정보 줄은 군종과 지금 계급(예정)`() {
        // Figma 예시: 공군, 입대 2026.04.20, 오늘 2026.10.28 → 일병.
        val service = Service("s", Branch.AIR_FORCE, LocalDate(2026, 4, 20), 1)
        val timeline = ServiceCalculator().timeline(service)
        assertEquals("공군 · 일병(예정)", militaryLabel(service, timeline, LocalDate(2026, 10, 28)))
        assertEquals("입력 전", militaryLabel(null, null, LocalDate(2026, 10, 28)))
    }

    @Test
    fun `진급일 · 전역일은 지남 · D-day · D-N`() {
        val today = LocalDate(2026, 10, 28)
        assertEquals("지남", ddayLabel(today, LocalDate(2026, 6, 20)))
        assertEquals("D-day", ddayLabel(today, today))
        assertEquals("D-53", ddayLabel(today, LocalDate(2026, 12, 20)))
        assertEquals("D-479", ddayLabel(today, LocalDate(2028, 2, 19)))
    }

    @Test
    fun `꽃신 평생 줄은 값 · 이용 중 · 함께 이용 중`() {
        assertEquals("7,900원", lifetimeLabel(owned = false, shared = false, price = "7,900원"))
        assertEquals("이용 중", lifetimeLabel(owned = true, shared = true, price = "7,900원"))
        assertEquals("함께 이용 중", lifetimeLabel(owned = false, shared = true, price = "7,900원"))
    }

    @Test
    fun `이름은 앞뒤 공백을 빼고 10자까지`() {
        assertEquals("가나다라마바사아자차", nicknameInput("  가나다라마바사아자차카"))
    }
}
