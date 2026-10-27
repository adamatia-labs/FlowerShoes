package com.sadturtleman.flowershoes.feature.onboarding

import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingNavigationTest {
    private fun state(role: Role, step: OnboardingStep) = OnboardingState(
        role = role, step = step, today = LocalDate(2026, 10, 24),
    )

    @Test fun gomsinSkipsServiceInBothDirections() {
        assertEquals(OnboardingStep.CONNECT, state(Role.GOMSIN, OnboardingStep.THEME).nextStep())
        assertEquals(OnboardingStep.THEME, state(Role.GOMSIN, OnboardingStep.CONNECT).previousStep())
        assertEquals(3, state(Role.GOMSIN, OnboardingStep.CONNECT).totalSteps)
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
