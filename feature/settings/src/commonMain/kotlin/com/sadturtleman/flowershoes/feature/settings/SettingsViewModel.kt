package com.sadturtleman.flowershoes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.backup.BackupManager
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.data.NotifyPrefsTransport
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** 백업 화면 상태(설정 › 백업). */
data class BackupInfo(
    val available: Boolean = false,
    val lastAtMs: Long? = null,
    val bytes: Long = 0,
    val busy: Boolean = false,
)

data class SettingsState(
    val theme: ThemeAccent = ThemeAccent.ROSE,
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
    val daysTogether: Int? = null,
    val service: Service? = null,
    val timeline: ServiceTimeline? = null,
    val provider: String? = null,
    val notify: NotificationPrefs = NotificationPrefs(),
    val autoBackup: Boolean = true,
    val cellularBackup: Boolean = false,
    val codeSeen: Boolean = false,
    val backup: BackupInfo = BackupInfo(),
)

/**
 * 설정(Figma 7 · 설정). 화면마다 쪼갠 상태 대신 하나로 — 설정 값은 모두 작고, 하위 화면이 같은 값을 나눠 쓴다.
 */
class SettingsViewModel(
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
    private val unlink: UnlinkService,
    private val identity: Identity,
    private val backup: BackupManager? = null,
    private val notifyTransport: NotifyPrefsTransport? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val backupInfo = MutableStateFlow(BackupInfo(available = backup != null))
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val notices: SharedFlow<String> = _notices

    private val people = combine(profile.observeCouple(), profile.observeUsers()) { couple, users -> couple to users }

    val state: StateFlow<SettingsState> = combine(
        combine(settings.state, settings.displayMode, settings.accountProvider) { d, mode, provider -> Triple(d, mode, provider) },
        profile.observeTimelines(),
        people,
        combine(settings.notifyPrefs, settings.autoBackup, settings.cellularBackup, settings.recoveryCodeSeen, settings.marketingConsent) { n, a, c, s, m -> listOf(n, a, c, s, m) },
        backupInfo,
    ) { (d, mode, provider), timelines, (couple, users), flags, b ->
        val me = identity.userId
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val today = clock.today()
        val since = couple?.linkedAtMs?.takeIf { partnerId != null }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(SEOUL).date }
        val anniversary = couple?.anniversary?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        SettingsState(
            theme = d.theme,
            displayMode = mode,
            role = d.role,
            myName = users.firstOrNull { it.id == me }?.nickname?.takeIf { it.isNotBlank() } ?: "나",
            partnerName = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인",
            partnerDevice = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.deviceName?.takeIf { it.isNotBlank() },
            marketing = flags[4] as Boolean,
            linked = partnerId != null,
            linkedSince = since,
            anniversary = anniversary,
            daysTogether = (anniversary ?: since)?.let { it.daysUntil(today) + 1 },
            service = timelines.firstOrNull()?.first,
            timeline = timelines.firstOrNull()?.second,
            provider = provider,
            notify = flags[0] as NotificationPrefs,
            autoBackup = flags[1] as Boolean,
            cellularBackup = flags[2] as Boolean,
            codeSeen = flags[3] as Boolean,
            backup = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        refreshBackup()
    }

    private fun refreshBackup() {
        val m = backup ?: return
        viewModelScope.launch {
            val preview = runCatching { m.preview(identity.userId) }.getOrNull()
            backupInfo.update { it.copy(lastAtMs = preview?.createdAtMs, bytes = preview?.summary?.bytes ?: 0) }
        }
    }

    fun setTheme(theme: ThemeAccent) = viewModelScope.launch { settings.setTheme(theme) }

    /** 내 이름(연인 폰의 "민준 ♥ 지수" · 편지 · 알림에 쓰인다). */
    fun setNickname(name: String) {
        val role = state.value.role ?: return
        viewModelScope.launch { profile.saveMe(role, name.trim().take(10)) }
    }

    fun setDisplayMode(mode: String) = viewModelScope.launch { settings.setDisplayMode(mode) }

    fun setAnniversary(date: LocalDate?) = viewModelScope.launch { profile.setAnniversary(date) }

    /** 군종 · 입대일 고치기(공유 데이터, LWW — 연인 폰에도 같이 바뀐다). */
    fun updateService(branch: Branch? = null, enlist: LocalDate? = null) {
        val s = state.value.service ?: return
        viewModelScope.launch { profile.saveService(s.copy(branch = branch ?: s.branch, enlistDate = enlist ?: s.enlistDate)) }
    }

    /** 조기 전역 · 연장 같은 사용자 수정값. null 이면 규칙으로 계산한 날로 돌아간다. */
    fun overrideDischarge(date: LocalDate?) {
        val s = state.value.service ?: return
        viewModelScope.launch { profile.saveService(s.copy(dischargeOverride = date)) }
    }

    /** 알림 설정: 이 기기에 저장하고 서버(받을 종류 · 조용한 시간 · 묶음)에 올린다. */
    fun setNotify(prefs: NotificationPrefs, platform: PlatformActions) {
        platform.setHidePreview(prefs.hidePreview)
        viewModelScope.launch {
            settings.setNotifyPrefs(prefs)
            runCatching { notifyTransport?.upload(prefs) }
        }
    }

    /** 알림 받기(설정 › 앱): Figma 최종 정리는 켜기/끄기 하나 — 받을 종류를 한꺼번에 바꾼다. */
    fun setAllNotify(on: Boolean, platform: PlatformActions) {
        val n = state.value.notify
        setNotify(n.copy(letters = on, missYou = on, status = on, eventEve = on, anniversary = on), platform)
    }

    fun setAnniversaryAlerts(hundreds: Boolean, years: Boolean, platform: PlatformActions) {
        setNotify(state.value.notify.copy(anniversary100 = hundreds, anniversaryYear = years), platform)
    }

    fun setMarketing(on: Boolean) = viewModelScope.launch { settings.setMarketingConsent(on) }

    fun setAutoBackup(on: Boolean) = viewModelScope.launch { settings.setAutoBackup(on) }

    fun setCellularBackup(on: Boolean) = viewModelScope.launch { settings.setCellularBackup(on) }

    fun backupNow() {
        val m = backup ?: return
        if (backupInfo.value.busy) return
        viewModelScope.launch {
            backupInfo.update { it.copy(busy = true) }
            runCatching { m.backupNow(identity.userId) }
                .onSuccess { manifest -> backupInfo.update { it.copy(lastAtMs = manifest.createdAtMs, bytes = manifest.summary.bytes) }; _notices.tryEmit("백업했어요") }
                .onFailure { _notices.tryEmit("백업하지 못했어요. 잠시 뒤 다시 해 주세요") }
            backupInfo.update { it.copy(busy = false) }
        }
    }

    /** 복구 코드(기기 인증 뒤 화면이 부른다). 아직 백업하지 않았으면 열쇠를 만들고 코드를 만든다. */
    suspend fun recoveryCode(): String? {
        val m = backup ?: return null
        val code = runCatching { m.recoveryCode() ?: run { m.ensureKey(identity.userId); m.recoveryCode() } }.getOrNull()
        if (code != null) settings.setRecoveryCodeSeen()
        return code
    }

    suspend fun regenerateCode(): String? {
        val m = backup ?: return null
        return runCatching { m.regenerateRecoveryCode(identity.userId) }.getOrNull()?.also { _notices.tryEmit("새 코드를 만들었어요. 옛 코드는 더 이상 쓸 수 없어요") }
    }

    fun notice(message: String) {
        _notices.tryEmit(message)
    }

    /** 되돌릴 수 없다. 화면이 확인 문구 입력(+ 기기 인증)을 받은 뒤에만 부른다. */
    fun unlink(after: AfterUnlink) = viewModelScope.launch { unlink.unlink(after) }

    /** 상대가 해제한 뒤 이 폰 사본을 어떻게 할지. */
    fun afterPartnerUnlinked(after: AfterUnlink) = viewModelScope.launch { unlink.onPartnerUnlinked(after) }

    /** 탈퇴. 끝나면 설정이 비워져 셸이 처음 화면(로그인)으로 돌아간다. */
    fun deleteAccount(deleteSyncedBackupKey: suspend () -> Unit) = viewModelScope.launch { unlink.deleteAccount(deleteSyncedBackupKey) }

    /** 상대 폰 교체 알림의 "카카오 계정으로 로그인" 문구. */
    fun providerLabel(provider: String?): String = when (provider) {
        "KAKAO" -> "카카오"
        "APPLE" -> "Apple"
        "GOOGLE" -> "Google"
        else -> "이메일"
    }

    suspend fun currentProvider(): String? = settings.accountProvider.first()
}
