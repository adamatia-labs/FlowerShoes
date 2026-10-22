package com.sadturtleman.flowershoes.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.PENDING_SOLDIER
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** 온보딩 순서: 로그인 → 역할 → 테마 → 입대일 → 연결. 백업이 있는 계정은 로그인 뒤 바로 복원으로 간다. */
enum class OnboardingStep { LOGIN, ROLE, THEME, SERVICE, CONNECT }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.LOGIN,
    val providers: List<AuthProvider> = AuthProvider.entries,
    val signingIn: Boolean = false,
    val role: Role? = null,
    val theme: ThemeAccent? = null,
    val branch: Branch = Branch.ARMY,
    val enlistDate: LocalDate? = null,
    val error: String? = null,
) {
    /** 입대일을 고르면 바로 보여 주는 전역일 미리보기. */
    val dischargePreview: LocalDate?
        get() = enlistDate?.let {
            ServiceCalculator().timeline(Service("preview", branch, it, ServiceRulesTable.BUNDLED.version)).dischargeDate
        }

    val canProceed: Boolean
        get() = when (step) {
            OnboardingStep.LOGIN -> false
            OnboardingStep.ROLE -> role != null
            OnboardingStep.THEME -> theme != null
            OnboardingStep.SERVICE -> enlistDate != null
            OnboardingStep.CONNECT -> true
        }
}

sealed interface OnboardingIntent {
    data class SignIn(val provider: AuthProvider) : OnboardingIntent
    data class SelectRole(val role: Role) : OnboardingIntent
    data class SelectTheme(val theme: ThemeAccent) : OnboardingIntent
    data class SelectBranch(val branch: Branch) : OnboardingIntent
    data class SetEnlistDate(val date: LocalDate) : OnboardingIntent
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent

    /** "혼자 먼저 둘러볼게요". 연결은 나중에 설정에서 한다. */
    data object FinishSolo : OnboardingIntent

    /** 초대 코드로 연결. */
    data object StartPairing : OnboardingIntent

    /** 폰을 바꿨다 — 백업에서 복원. */
    data object OpenRestore : OnboardingIntent
}

sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
    data object OpenRestore : OnboardingEvent
    data object OpenPairing : OnboardingEvent
}

class OnboardingViewModel(
    private val auth: AuthGateway,
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState(providers = auth.providers))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.SignIn -> signIn(intent.provider)
            is OnboardingIntent.SelectRole -> _state.update {
                // 역할을 바꾸면 테마 기본값도 따라간다(이미 고른 테마는 두지 않는다 — 다음 화면에서 다시 고른다).
                it.copy(role = intent.role, theme = ThemeAccent.defaultFor(intent.role))
            }
            is OnboardingIntent.SelectTheme -> {
                _state.update { it.copy(theme = intent.theme) }
                // 바로 저장해 앱 전체 색이 미리보기처럼 바뀌게 한다.
                viewModelScope.launch { settings.setTheme(intent.theme) }
            }
            is OnboardingIntent.SelectBranch -> _state.update { it.copy(branch = intent.branch) }
            is OnboardingIntent.SetEnlistDate -> _state.update { it.copy(enlistDate = intent.date) }
            OnboardingIntent.Next -> next()
            OnboardingIntent.Back -> _state.update { s ->
                s.copy(step = OnboardingStep.entries.getOrElse(s.step.ordinal - 1) { s.step }.coerceAtLeast(OnboardingStep.ROLE))
            }
            OnboardingIntent.FinishSolo -> finish(OnboardingEvent.Finished)
            OnboardingIntent.StartPairing -> finish(OnboardingEvent.OpenPairing)
            OnboardingIntent.OpenRestore -> viewModelScope.launch { _events.send(OnboardingEvent.OpenRestore) }
        }
    }

    private fun signIn(provider: AuthProvider) {
        _state.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.signIn(provider)) {
                is SignInResult.Success -> {
                    if (r.uid.isNotEmpty()) settings.bindAccount(r.uid)
                    if (r.hasBackup) {
                        _events.send(OnboardingEvent.OpenRestore)
                    } else {
                        _state.update { it.copy(signingIn = false, step = OnboardingStep.ROLE) }
                    }
                }
                SignInResult.Cancelled -> _state.update { it.copy(signingIn = false) }
                is SignInResult.Failed -> _state.update { it.copy(signingIn = false, error = r.message) }
            }
        }
    }

    private fun next() {
        val s = _state.value
        if (!s.canProceed) return
        viewModelScope.launch {
            when (s.step) {
                OnboardingStep.ROLE -> settings.setRole(checkNotNull(s.role))
                OnboardingStep.THEME -> settings.setTheme(checkNotNull(s.theme))
                else -> Unit
            }
            _state.update { it.copy(step = OnboardingStep.entries[minOf(s.step.ordinal + 1, OnboardingStep.entries.lastIndex)]) }
        }
    }

    private fun finish(event: OnboardingEvent) {
        val s = _state.value
        viewModelScope.launch {
            val role = s.role ?: Role.GOMSIN
            profile.saveMe(role, nickname = "")
            s.enlistDate?.let { date ->
                // 곰신이 입력한 값은 아직 모르는 상대(군인)의 정보다. 연결할 때 군인이 입력한 값이 우선한다(7-1-1).
                val owner = if (role == Role.SOLDIER) profile.myUserId() else PENDING_SOLDIER
                profile.saveService(Service(owner, s.branch, date, ServiceRulesTable.BUNDLED.version))
            }
            settings.setOnboarded()
            _events.send(event)
        }
    }

}
