package com.sadturtleman.flowershoes.feature.onboarding

import com.sadturtleman.flowershoes.core.data.PENDING_SOLDIER
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingNavigationTest {
    private fun state(role: Role, step: OnboardingStep) = OnboardingState(
        role = role, step = step, today = LocalDate(2026, 10, 24),
    )

    @Test fun gomsinEntersPartnerServiceBetweenThemeAndConnect() {
        assertEquals(OnboardingStep.SERVICE, state(Role.GOMSIN, OnboardingStep.THEME).nextStep())
        assertEquals(OnboardingStep.CONNECT, state(Role.GOMSIN, OnboardingStep.SERVICE).nextStep())
        assertEquals(OnboardingStep.SERVICE, state(Role.GOMSIN, OnboardingStep.CONNECT).previousStep())
        assertEquals(4, state(Role.GOMSIN, OnboardingStep.CONNECT).totalSteps)
    }

    @Test fun gomsinPartnerServiceIsSavedInThePendingSoldierSlot() {
        val s = state(Role.GOMSIN, OnboardingStep.CONNECT).copy(branch = Branch.AIR_FORCE, enlistDate = LocalDate(2026, 11, 9))
        assertEquals(Service(PENDING_SOLDIER, Branch.AIR_FORCE, LocalDate(2026, 11, 9), ServiceRulesTable.BUNDLED.version), s.serviceToSave("me"))
    }

    @Test fun soldierServiceIsSavedUnderTheirOwnId() {
        val s = state(Role.SOLDIER, OnboardingStep.CONNECT).copy(enlistDate = LocalDate(2026, 4, 20))
        assertEquals("me", s.serviceToSave("me")?.userId)
    }

    @Test fun gomsinWhoSkipsPartnerServiceSavesNothing() {
        assertNull(state(Role.GOMSIN, OnboardingStep.CONNECT).serviceToSave("me"))
    }

    @Test fun startStepNudgesOnlyBeforeEnlistment() {
        val connect = state(Role.GOMSIN, OnboardingStep.CONNECT)
        assertTrue(connect.copy(enlistDate = LocalDate(2026, 11, 9)).beforeEnlistment)
        assertFalse(connect.copy(enlistDate = LocalDate(2026, 10, 24)).beforeEnlistment)
        assertFalse(connect.beforeEnlistment)
    }

    @Test fun nicknameFallsBackToTheSocialLoginName() {
        val s = state(Role.GOMSIN, OnboardingStep.CONNECT)
        assertEquals("지수", s.copy(loginName = "  지수 ").nickname)
        assertEquals("가나다라마바사아자차", s.copy(loginName = "가나다라마바사아자차카").nickname)
        assertEquals("", s.nickname)
    }

    @Test fun soldierMustSelectServiceDateBeforeConnecting() {
        val service = state(Role.SOLDIER, OnboardingStep.SERVICE)
        assertEquals(OnboardingStep.SERVICE, state(Role.SOLDIER, OnboardingStep.THEME).nextStep())
        assertEquals(OnboardingStep.SERVICE, state(Role.SOLDIER, OnboardingStep.CONNECT).previousStep())
        assertFalse(service.canProceed)
        assertTrue(service.copy(enlistDate = LocalDate(2026, 10, 1)).canProceed)
        assertEquals(4, service.totalSteps)
    }

    @Test fun requiredTermsGateProgressWithoutMarketing() {
        val terms = state(Role.GOMSIN, OnboardingStep.TERMS)
        assertFalse(terms.canProceed)
        assertTrue(terms.copy(agreed = Agreement.entries.filter { it.required }.toSet()).canProceed)
    }
}
