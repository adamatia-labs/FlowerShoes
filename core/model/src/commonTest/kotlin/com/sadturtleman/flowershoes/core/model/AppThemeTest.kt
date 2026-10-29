package com.sadturtleman.flowershoes.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemeTest {

    @Test
    fun 저장된_테마를_그대로_읽는다() {
        assertEquals(AppTheme.SOLDIER, AppTheme.from("SOLDIER", Role.GOMSIN))
        assertEquals(AppTheme.GOMSIN, AppTheme.from("GOMSIN", Role.SOLDIER))
    }

    @Test
    fun 예전_강조색_값은_역할_기본값으로_바꾼다() {
        assertEquals(AppTheme.GOMSIN, AppTheme.from("NAVY", Role.GOMSIN))
        assertEquals(AppTheme.SOLDIER, AppTheme.from("ROSE", Role.SOLDIER))
    }

    @Test
    fun 아무것도_없으면_역할_기본값_역할도_없으면_곰신() {
        assertEquals(AppTheme.SOLDIER, AppTheme.from(null, Role.SOLDIER))
        assertEquals(AppTheme.GOMSIN, AppTheme.from(null, null))
    }
}
