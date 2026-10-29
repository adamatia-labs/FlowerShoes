package com.sadturtleman.flowershoes.feature.onboarding

import com.sadturtleman.flowershoes.core.designsystem.TermsDoc

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
 * 온보딩 순서(Figma 1 · 로그인 · 온보딩 · 연결): 로그인 → 약관 동의 → 역할 → 테마 → 복무 정보 → 시작 방법(초대 · 혼자).
 * 복무 정보는 군인이면 내 입대일(필수), 곰신이면 연인의 입대일(345:642, "나중에 넣을게요"로 건너뛸 수 있다).
 * 단계 표시는 역할부터 4칸.
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
    /** 시작 방법(346:451): 연인 초대하기(기본) · 혼자 먼저 시작하기. */
    val startWithInvite: Boolean = true,
    val today: LocalDate,
    /** 소셜 로그인이 알려 준 이름. 온보딩에 이름 단계가 없어(Figma) 내 이름의 처음 값으로 쓴다. */
    val loginName: String? = null,
    val error: String? = null,
) {
    /** 입대일을 고르면 바로 보여 주는 전역일 미리보기. */
    val preview: ServiceTimeline?
        get() = enlistDate?.let { ServiceCalculator().timeline(Service("preview", branch, it, ServiceRulesTable.BUNDLED.version)) }

    val totalSteps: Int get() = 4

    fun nextStep(): OnboardingStep = OnboardingStep.entries[minOf(step.ordinal + 1, OnboardingStep.entries.lastIndex)]

    fun previousStep(): OnboardingStep =
        if (step.ordinal <= OnboardingStep.TERMS.ordinal) step else OnboardingStep.entries[step.ordinal - 1]

    /** 입대일이 아직 오지 않았다: 시작 방법에 "입대 전에 같이 연결해 두세요"(346:487). */
    val beforeEnlistment: Boolean get() = enlistDate?.let { it > today } == true

    /** 처음 내 이름: 소셜 로그인 이름(10자). 없으면 빈 이름 — 화면은 "나" · "연인"으로 보인다. */
    val nickname: String get() = loginName?.trim()?.take(10).orEmpty()

    /**
     * 저장할 복무 정보. 군인은 내 것, 곰신은 연결 전이라 군인 uid 를 몰라 [PENDING_SOLDIER] 자리에 둔다
     * (연결하면 군인 쪽으로 옮겨지고, 군인이 직접 넣은 값이 이긴다).
     */
    fun serviceToSave(myUserId: String): Service? = enlistDate?.let { date ->
        Service(if (role == Role.SOLDIER) myUserId else PENDING_SOLDIER, branch, date, ServiceRulesTable.BUNDLED.version)
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
    data class SelectStart(val invite: Boolean) : OnboardingIntent

    /** 곰신: 연인 복무 정보 "나중에 넣을게요". */
    data object SkipService : OnboardingIntent
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent

    /** 혼자 먼저 시작하기. 연결은 나중에 홈 · 설정에서 한다. */
    data object FinishSolo : OnboardingIntent

    /** 연인 초대(보내는 쪽) 또는 받은 초대 링크 수락으로 넘어간다. */
    data object StartPairing : OnboardingIntent
}

sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
    data object OpenPairing : OnboardingEvent

    /** 이미 다른 폰에서 연결된 계정으로 로그인했다 → 기기 교체(#20). 약관 · 역할은 옛 폰에서 마쳤다. */
    data object DeviceChange : OnboardingEvent
}

class OnboardingViewModel(
    private val auth: AuthGateway,
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
    clock: Clock = Clock.System,
    /** 로그인한 계정이 이미 다른 폰에서 연결돼 있는지(서버). 확인하지 못하면 false. */
    private val linkedElsewhere: suspend () -> Boolean = { false },
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
                it.copy(role = intent.role, theme = AppTheme.defaultFor(intent.role), enlistDate = if (intent.role == it.role) it.enlistDate else null)
            }
            is OnboardingIntent.SelectTheme -> {
                _state.update { it.copy(theme = intent.theme) }
                // 바로 저장해 앱 전체 색이 미리보기처럼 바뀌게 한다.
                viewModelScope.launch { settings.setTheme(intent.theme) }
            }
            is OnboardingIntent.SelectBranch -> _state.update { it.copy(branch = intent.branch) }
            is OnboardingIntent.SetEnlistDate -> _state.update { it.copy(enlistDate = intent.date) }
            is OnboardingIntent.SelectStart -> _state.update { it.copy(startWithInvite = intent.invite) }
            OnboardingIntent.SkipService -> _state.update {
                if (it.step == OnboardingStep.SERVICE && it.role == Role.GOMSIN) it.copy(enlistDate = null, step = it.nextStep()) else it
            }
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
        }
    }

    private fun signIn(provider: AuthProvider) {
        if (_state.value.signingIn) return
        _state.update { it.copy(signingIn = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.signIn(provider)) {
                is SignInResult.Success -> {
                    if (r.uid.isNotEmpty()) settings.bindAccount(r.uid, r.provider.name)
                    if (runCatching { linkedElsewhere() }.getOrDefault(false)) {
                        _state.update { it.copy(signingIn = false, loginName = r.displayName) }
                        _events.send(OnboardingEvent.DeviceChange)
                    } else {
                        _state.update { it.copy(signingIn = false, step = OnboardingStep.TERMS, loginName = r.displayName) }
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
        if (s.step == OnboardingStep.CONNECT) return finish(if (s.startWithInvite) OnboardingEvent.OpenPairing else OnboardingEvent.Finished)
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
            profile.saveMe(role, nickname = s.nickname)
            s.copy(role = role).serviceToSave(profile.myUserId())?.let { profile.saveService(it) }
            // 초대 화면은 온보딩 위에 뜬다. 여기서 먼저 완료 처리하면 셸이 교체되어 그 화면이 사라진다
            // — 보내기 · 연결을 마치면 셸이 완료 처리한다.
            if (event == OnboardingEvent.Finished) settings.setOnboarded()
            _events.send(event)
        }
    }
}
