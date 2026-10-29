package com.sadturtleman.flowershoes.feature.onboarding

import com.sadturtleman.flowershoes.core.designsystem.TermsDoc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.auth.SignInResult
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 온보딩 순서(Figma ✅ 최종 화면 · 1 온보딩): 로그인 → 약관 동의 → 역할 → 테마 → 입대일(군인만) → 연결.
 * 단계 표시는 역할부터(곰신 3칸 · 군인 4칸). 백업이 있는 계정은 로그인 뒤 바로 복원으로 간다.
 */
enum class OnboardingStep { LOGIN, TERMS, ROLE, THEME, SERVICE, CONNECT }

/** 약관 항목. [required] 를 모두 동의해야 시작할 수 있다. */
enum class Agreement(val label: String, val required: Boolean, val doc: TermsDoc?) {
    AGE("(필수) 만 14세 이상이에요", true, null),
    SERVICE("(필수) 서비스 이용약관", true, TermsDoc.SERVICE),
    PRIVACY("(필수) 개인정보 수집 · 이용 동의", true, TermsDoc.PRIVACY),
    MARKETING("(선택) 이벤트 · 혜택 알림 받기", false, TermsDoc.MARKETING),
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.LOGIN,
    val providers: List<AuthProvider> = AuthProvider.entries,
    val signingIn: Boolean = false,
    val agreed: Set<Agreement> = emptySet(),
    /** 약관 보기(온보딩 0-2)가 열려 있으면 그 문서. */
    val reading: TermsDoc? = null,
    val role: Role? = null,
    val theme: AppTheme? = null,
    val branch: Branch = Branch.ARMY,
    val enlistDate: LocalDate? = null,
    val today: LocalDate,
    val error: String? = null,
) {
    /** 입대일을 고르면 바로 보여 주는 전역일 미리보기. */
    val preview: ServiceTimeline?
        get() = enlistDate?.let { ServiceCalculator().timeline(Service("preview", branch, it, ServiceRulesTable.BUNDLED.version)) }

    val totalSteps: Int get() = if (role == Role.GOMSIN) 3 else 4

    fun nextStep(): OnboardingStep = when {
        step == OnboardingStep.THEME && role == Role.GOMSIN -> OnboardingStep.CONNECT
        else -> OnboardingStep.entries[minOf(step.ordinal + 1, OnboardingStep.entries.lastIndex)]
    }

    fun previousStep(): OnboardingStep = when {
        step.ordinal <= OnboardingStep.TERMS.ordinal -> step
        step == OnboardingStep.CONNECT && role == Role.GOMSIN -> OnboardingStep.THEME
        else -> OnboardingStep.entries[step.ordinal - 1]
    }

    val allAgreed: Boolean get() = agreed.containsAll(Agreement.entries)

    val canProceed: Boolean
        get() = when (step) {
            OnboardingStep.LOGIN -> false
            OnboardingStep.TERMS -> Agreement.entries.filter { it.required }.all { it in agreed }
            OnboardingStep.ROLE -> role != null
            OnboardingStep.THEME -> theme != null
            OnboardingStep.SERVICE -> enlistDate != null
            OnboardingStep.CONNECT -> true
        }
}

sealed interface OnboardingIntent {
    data class SignIn(val provider: AuthProvider) : OnboardingIntent
    data class ToggleAgreement(val item: Agreement) : OnboardingIntent
    data object ToggleAll : OnboardingIntent
    data class Read(val doc: TermsDoc?) : OnboardingIntent
    data class SelectRole(val role: Role) : OnboardingIntent
    data class SelectTheme(val theme: AppTheme) : OnboardingIntent
    data class SelectBranch(val branch: Branch) : OnboardingIntent
    data class SetEnlistDate(val date: LocalDate) : OnboardingIntent
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent

    /** "혼자 먼저 둘러볼게요". 연결은 나중에 홈 · 설정에서 한다. */
    data object FinishSolo : OnboardingIntent

    /** 연결 단계에서 상대와 확인 코드(SAS) 비교로 넘어간다. */
    data object StartPairing : OnboardingIntent

    /** 폰을 바꿨다 — 백업에서 복원. */
    data object OpenRestore : OnboardingIntent

    /** 복원 · 백업 없음 › "다른 방법으로 로그인했었나요?" — 로그아웃하고 처음 화면으로. */
    data object SignOut : OnboardingIntent
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
    clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingState(providers = auth.providers, today = clock.now().toLocalDateTime(TimeZone.of("Asia/Seoul")).date),
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.SignIn -> signIn(intent.provider)
            is OnboardingIntent.ToggleAgreement -> _state.update {
                it.copy(agreed = if (intent.item in it.agreed) it.agreed - intent.item else it.agreed + intent.item)
            }
            OnboardingIntent.ToggleAll -> _state.update { it.copy(agreed = if (it.allAgreed) emptySet() else Agreement.entries.toSet()) }
            is OnboardingIntent.Read -> _state.update { it.copy(reading = intent.doc) }
            is OnboardingIntent.SelectRole -> _state.update {
                // 역할을 바꾸면 테마 기본값도 따라간다(곰신 로즈 · 군인 올리브, 화면에 "추천").
                it.copy(role = intent.role, theme = AppTheme.defaultFor(intent.role), enlistDate = if (intent.role == Role.SOLDIER) it.enlistDate else null)
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
                when {
                    s.reading != null -> s.copy(reading = null)
                    s.step.ordinal <= OnboardingStep.TERMS.ordinal -> s
                    else -> s.copy(step = s.previousStep())
                }
            }
            OnboardingIntent.FinishSolo -> finish(OnboardingEvent.Finished)
            OnboardingIntent.StartPairing -> finish(OnboardingEvent.OpenPairing)
            OnboardingIntent.OpenRestore -> viewModelScope.launch { _events.send(OnboardingEvent.OpenRestore) }
            OnboardingIntent.SignOut -> viewModelScope.launch {
                runCatching { auth.signOut() }
                _state.update { it.copy(step = OnboardingStep.LOGIN, agreed = emptySet()) }
            }
        }
    }

    private fun signIn(provider: AuthProvider) {
        if (_state.value.signingIn) return
        _state.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.signIn(provider)) {
                is SignInResult.Success -> {
                    if (r.uid.isNotEmpty()) settings.bindAccount(r.uid, r.provider.name)
                    if (r.hasBackup) {
                        // 복원 화면에서 "처음 시작하기"로 돌아오면 약관부터.
                        _state.update { it.copy(signingIn = false, step = OnboardingStep.TERMS) }
                        _events.send(OnboardingEvent.OpenRestore)
                    } else {
                        _state.update { it.copy(signingIn = false, step = OnboardingStep.TERMS) }
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
                OnboardingStep.TERMS -> settings.setMarketingConsent(Agreement.MARKETING in s.agreed)
                OnboardingStep.ROLE -> settings.setRole(checkNotNull(s.role))
                OnboardingStep.THEME -> settings.setTheme(checkNotNull(s.theme))
                else -> Unit
            }
            _state.update { it.copy(step = s.nextStep()) }
        }
    }

    private fun finish(event: OnboardingEvent) {
        val s = _state.value
        viewModelScope.launch {
            val role = s.role ?: Role.GOMSIN
            profile.saveMe(role, nickname = "")
            s.enlistDate?.takeIf { role == Role.SOLDIER }?.let { date ->
                profile.saveService(Service(profile.myUserId(), s.branch, date, ServiceRulesTable.BUNDLED.version))
            }
            // 연결은 확인 코드(SAS) 비교까지 온보딩 안에서 마친다.
            // 여기서 먼저 완료 처리하면 셸이 교체되어 OpenPairing 화면이 사라진다.
            if (event == OnboardingEvent.Finished) settings.setOnboarded()
            _events.send(event)
        }
    }
}
