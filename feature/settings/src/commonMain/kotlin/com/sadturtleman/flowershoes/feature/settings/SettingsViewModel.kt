package com.sadturtleman.flowershoes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.atTime
import com.sadturtleman.flowershoes.core.data.PENDING_SOLDIER
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.data.NotifyPrefsTransport
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

data class SettingsState(
    val theme: AppTheme = AppTheme.GOMSIN,
    val displayMode: String = "SYSTEM",
    val role: Role? = null,
    val myName: String = "나",
    val partnerName: String = "연인",
    /** 연인 폰 기종(연결 정보). 상대가 아직 올리지 않았으면 null. */
    val partnerDevice: String? = null,
    /** 이벤트 · 혜택 알림 동의(약관 동의의 선택 항목). */
    val marketing: Boolean = false,
    val linked: Boolean = false,
    val linkedSince: LocalDate? = null,
    val anniversary: LocalDate? = null,
    /** 직접 추가한 기념일(생일 · 처음 만난 날). 커플 기념일은 [anniversary]. */
    val customAnniversaries: List<Event> = emptyList(),
    val daysTogether: Int? = null,
    val service: Service? = null,
    val timeline: ServiceTimeline? = null,
    val provider: String? = null,
    val notify: NotificationPrefs = NotificationPrefs(),
    /** 서울 기준 오늘(계급 · D-day 계산). ViewModel 시계에서 온다. */
    val today: LocalDate? = null,
)

/**
 * 설정(Figma 7 · 설정). 화면마다 쪼갠 상태 대신 하나로 — 설정 값은 모두 작고, 하위 화면이 같은 값을 나눠 쓴다.
 */
class SettingsViewModel(
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
    private val unlink: UnlinkService,
    private val identity: Identity,
    private val events: EventRepository,
    private val notifyTransport: NotifyPrefsTransport? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** 토스트 한 번. [error] 면 오류 토스트(구매 실패 등). */
    data class Notice(val message: String, val error: Boolean = false)

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 2)
    val notices: SharedFlow<Notice> = _notices

    private val people = combine(profile.observeCouple(), profile.observeUsers(), profile.observeAnniversary()) { couple, users, anniversary ->
        Triple(couple, users, anniversary)
    }

    val state: StateFlow<SettingsState> = combine(
        combine(settings.state, settings.displayMode, settings.accountProvider) { d, mode, provider -> Triple(d, mode, provider) },
        combine(profile.observeTimelines(), events.observeAnniversaries()) { t, a -> t to a },
        people,
        combine(settings.notifyPrefs, settings.marketingConsent) { n, m -> n to m },
    ) { (d, mode, provider), (timelines, customAnniversaries), (couple, users, anniversary), (notify, marketing) ->
        val me = identity.userId
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val today = clock.today()
        val since = couple?.linkedAtMs?.takeIf { partnerId != null }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(SEOUL).date }
        SettingsState(
            theme = d.theme,
            displayMode = mode,
            role = d.role,
            myName = users.firstOrNull { it.id == me }?.nickname?.takeIf { it.isNotBlank() } ?: "나",
            partnerName = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인",
            partnerDevice = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.deviceName?.takeIf { it.isNotBlank() },
            marketing = marketing,
            linked = partnerId != null,
            linkedSince = since,
            anniversary = anniversary,
            customAnniversaries = customAnniversaries,
            daysTogether = (anniversary ?: since)?.let { it.daysUntil(today) + 1 },
            service = timelines.firstOrNull()?.first,
            timeline = timelines.firstOrNull()?.second,
            provider = provider,
            notify = notify,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settings.setTheme(theme) }

    /** 내 이름(연인 폰의 "민준 ♥ 지수" · 편지 · 알림에 쓰인다). */
    fun setNickname(name: String) {
        val role = state.value.role ?: return
        val nickname = nicknameInput(name).ifBlank { return }
        viewModelScope.launch { profile.saveMe(role, nickname) }
    }

    fun setDisplayMode(mode: String) = viewModelScope.launch { settings.setDisplayMode(mode) }

    fun setAnniversary(date: LocalDate?) = viewModelScope.launch { profile.setAnniversary(date) }

    /**
     * 군 정보 › 수정 › 저장: 군종 · 입대일 · 전역일을 한 번에(공유 데이터, LWW — 연인 폰에도 같이 바뀐다).
     * 전역일은 계산한 날과 다를 때만 사용자 수정값(조기 전역 · 연장)으로 남는다.
     * 아직 없으면(곰신이 온보딩에서 "나중에 넣을게요") 새로 만든다: 군인 것 자리는 군인 uid, 연결 전 곰신이면 [PENDING_SOLDIER].
     */
    fun saveMilitary(branch: Branch, enlist: LocalDate, discharge: LocalDate?) {
        val s = state.value.service
        val override = dischargeOverrideFor(branch, enlist, discharge)
        viewModelScope.launch {
            val base = s ?: run {
                val owner = if (state.value.role == Role.SOLDIER) identity.userId
                else profile.observePartnerId().first()?.takeIf { it.isNotBlank() } ?: PENDING_SOLDIER
                Service(owner, branch, enlist, ServiceRulesTable.BUNDLED.version)
            }
            profile.saveService(base.copy(branch = branch, enlistDate = enlist, dischargeOverride = override))
        }
    }

    /** 기념일 › 직접 추가한 날: 새로 넣거나([id] null) 고친다. 기념일 일정 하나(캘린더에도 보인다). */
    fun saveAnniversary(id: String?, title: String, date: LocalDate, repeats: Boolean) {
        val name = title.trim().ifBlank { return }
        viewModelScope.launch {
            val old = id?.let { events.get(it) }
            val base = old ?: Event(
                id = events.newId(), type = EventType.ANNIVERSARY, title = name, start = date.atTime(0, 0), end = date.atTime(23, 59),
                alarm = false, updatedAt = clock.now(), lamport = 0, lastWriterDevice = "",
            )
            events.save(base.copy(title = name, start = date.atTime(0, 0), end = date.atTime(23, 59), repeatYearly = repeats, updatedAt = clock.now()))
        }
    }

    fun deleteAnniversary(id: String) = viewModelScope.launch { events.delete(id) }

    /** 알림 설정: 이 기기에 저장하고 서버(받을 종류 · 조용한 시간 · 묶음)에 올린다. */
    fun setNotify(prefs: NotificationPrefs, platform: PlatformActions) {
        platform.setHidePreview(prefs.hidePreview)
        viewModelScope.launch {
            settings.setNotifyPrefs(prefs)
            runCatching { notifyTransport?.upload(prefs) }
        }
    }

    /** 설정 › 알림 "편지 · 보고싶어 · 일정 알림": 받을 종류를 한꺼번에 바꾼다(3개월 기록 알림은 따로). */
    fun setNotifyMaster(on: Boolean, platform: PlatformActions) = setNotify(state.value.notify.withNotifyMaster(on), platform)

    /** 3개월마다 기록 받아 두기(설정 › 알림). 이 폰이 예약하는 로컬 알림이다. */
    fun setExportReminder(on: Boolean, platform: PlatformActions) = setNotify(state.value.notify.copy(exportReminder = on), platform)

    /** 내 기록 ZIP 을 공유 시트로 넘겼다: 3개월 알림을 오늘부터 다시 센다. */
    fun markExported() = viewModelScope.launch { settings.markExported(clock.now().toEpochMilliseconds()) }

    fun setMarketing(on: Boolean) = viewModelScope.launch { settings.setMarketingConsent(on) }

    fun notice(message: String, error: Boolean = false) {
        _notices.tryEmit(Notice(message, error))
    }

    /** 되돌릴 수 없다. 화면이 확인 문구 입력(+ 기기 인증)을 받은 뒤에만 부른다. */
    fun unlink(after: AfterUnlink) = viewModelScope.launch { unlink.unlink(after) }

    /** 상대가 해제한 뒤 이 폰 사본을 어떻게 할지. */
    fun afterPartnerUnlinked(after: AfterUnlink) = viewModelScope.launch { unlink.onPartnerUnlinked(after) }

    /** 탈퇴. 끝나면 설정이 비워져 셸이 처음 화면(로그인)으로 돌아간다. */
    fun deleteAccount() = viewModelScope.launch {
        runCatching { unlink.deleteAccount() }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            notice("탈퇴하지 못했어요. 연결을 확인하고 다시 해 주세요", error = true)
        }
    }

    /** 로그인 제공자 이름(로그인 계정 · 기기 교체 "카카오 계정 비밀번호 바꾸기"). */
    fun providerLabel(provider: String?): String = when (provider) {
        "KAKAO" -> "카카오"
        "APPLE" -> "Apple"
        "GOOGLE" -> "Google"
        else -> "이메일"
    }

    suspend fun currentProvider(): String? = settings.accountProvider.first()
}
