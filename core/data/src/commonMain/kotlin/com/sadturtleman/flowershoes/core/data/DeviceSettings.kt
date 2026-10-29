package com.sadturtleman.flowershoes.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 이 기기의 설정. 온보딩이 어디까지 왔는지 · 테마 · 로컬 신원. */
data class DeviceState(
    val userId: String,
    val deviceId: String,
    val role: Role?,
    val theme: ThemeAccent,
    val onboarded: Boolean,
    val keyEpoch: Int,
)

/**
 * 이 기기에만 저장하는 값(DataStore). 동기화하지 않는다 — 테마를 바꿔도 상대 화면은 그대로다.
 *
 * 로그인(:core:auth)이 붙기 전에는 첫 실행 때 만든 로컬 id 를 쓴다. 로그인하면 [bindAccount] 로 uid 에 묶는다.
 */
class DeviceSettings(private val store: DataStore<Preferences>) {

    val state: Flow<DeviceState> = store.data.map { p ->
        DeviceState(
            userId = p[USER_ID].orEmpty(),
            deviceId = p[DEVICE_ID].orEmpty(),
            role = p[ROLE]?.let(Role::valueOf),
            theme = p[THEME]?.let(ThemeAccent::valueOf) ?: ThemeAccent.ROSE,
            onboarded = p[ONBOARDED] == 1,
            keyEpoch = p[KEY_EPOCH] ?: 0,
        )
    }

    /** 첫 실행이면 로컬 id 를 만든다. 언제 불러도 같은 값을 돌려준다. */
    suspend fun ensureIdentity(): DeviceState {
        store.edit { p ->
            if (p[USER_ID] == null) p[USER_ID] = "local:${Uuid.random()}"
            if (p[DEVICE_ID] == null) p[DEVICE_ID] = Uuid.random().toString()
        }
        return state.first()
    }

    /** [provider] 는 로그인 방법(KAKAO · APPLE · GOOGLE) — 복원 · 설정 › 로그인 계정 화면에 보인다. */
    suspend fun bindAccount(uid: String, provider: String? = null) = store.edit {
        it[USER_ID] = uid
        if (provider != null) it[PROVIDER] = provider
    }

    val accountProvider: Flow<String?> = store.data.map { it[PROVIDER] }

    suspend fun setRole(role: Role) = store.edit {
        it[ROLE] = role.name
        // 테마를 아직 안 골랐으면 역할 기본값을 미리 넣어 둔다(곰신 로즈, 군인 올리브).
        if (it[THEME] == null) it[THEME] = ThemeAccent.defaultFor(role).name
    }

    suspend fun setTheme(theme: ThemeAccent) = store.edit { it[THEME] = theme.name }

    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = 1 }

    /** 약관 동의(온보딩 0-1)의 선택 항목: 이벤트 · 혜택 알림. 필수 항목은 동의해야 시작할 수 있어 따로 두지 않는다. */
    suspend fun setMarketingConsent(agreed: Boolean) = store.edit { it[MARKETING] = if (agreed) 1 else 0 }

    val marketingConsent: kotlinx.coroutines.flow.Flow<Boolean> = store.data.map { (it[MARKETING] ?: 0) == 1 }

    suspend fun setKeyEpoch(epoch: Int) = store.edit { it[KEY_EPOCH] = epoch }

    /** 편지 쓰기 › 임시저장(JSON). 이 기기에만 — 보내기 전 글은 서버에도 상대에게도 가지 않는다. null 이면 지운다. */
    suspend fun saveLetterDraft(json: String?) = store.edit { if (json == null) it.remove(LETTER_DRAFT) else it[LETTER_DRAFT] = json }

    val letterDraft: Flow<String?> = store.data.map { it[LETTER_DRAFT] }

    private val prefsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** 알림 설정(이 기기). 서버에 올리는 것은 [NotifyPrefsTransport] 가 따로 한다. */
    val notifyPrefs: Flow<NotificationPrefs> = store.data.map { p ->
        p[NOTIFY_PREFS]?.let { runCatching { prefsJson.decodeFromString(NotificationPrefs.serializer(), it) }.getOrNull() } ?: NotificationPrefs()
    }

    suspend fun setNotifyPrefs(prefs: NotificationPrefs) = store.edit { it[NOTIFY_PREFS] = prefsJson.encodeToString(NotificationPrefs.serializer(), prefs) }

    /** 설정 › 테마 › 화면 모드: SYSTEM(기본) · LIGHT · DARK. */
    val displayMode: Flow<String> = store.data.map { it[DISPLAY_MODE] ?: "SYSTEM" }

    suspend fun setDisplayMode(mode: String) = store.edit { it[DISPLAY_MODE] = mode }

    /** 받은 그림 위젯 프레임(설정 › 위젯, 기본 말고는 꽃신 평생): BASIC · BLOSSOM · KRAFT · POLAROID · NIGHT. */
    val widgetFrame: Flow<String> = store.data.map { it[WIDGET_FRAME] ?: "BASIC" }

    suspend fun setWidgetFrame(frame: String) = store.edit { it[WIDGET_FRAME] = frame }

    /** 추억 › 위젯에 다시 띄우기: 이 그림을 [atMs] 부터 위젯에. 그 뒤에 새 그림이 오면 새 그림이 이긴다. */
    suspend fun pinWidgetDrawing(drawingId: String, atMs: Long) = store.edit { it[WIDGET_PIN] = "$drawingId|$atMs" }

    val widgetPin: Flow<Pair<String, Long>?> = store.data.map { p ->
        p[WIDGET_PIN]?.split('|')?.takeIf { it.size == 2 }?.let { (id, at) -> at.toLongOrNull()?.let { id to it } }
    }

    /**
     * 위젯에 그림이 떠 있던 시간(추억 › 그림 상세 "위젯에 n시간 표시됨"). 위젯이 바뀔 때마다 부른다 —
     * [drawingId] 가 null 이면 위젯이 없거나 그림이 없는 것. 지난 그림의 시간을 닫고 새 그림의 시작을 적는다.
     */
    suspend fun widgetShowing(drawingId: String?, nowMs: Long) = store.edit { p ->
        val current = p[WIDGET_CURRENT]?.split('|')?.takeIf { it.size == 2 }
        if (current?.get(0) == drawingId) return@edit
        val totals = widgetJson.decodeFromString<Map<String, Long>>(p[WIDGET_TOTALS] ?: "{}").toMutableMap()
        current?.let { (id, since) -> since.toLongOrNull()?.let { totals[id] = (totals[id] ?: 0) + (nowMs - it).coerceAtLeast(0) } }
        p[WIDGET_TOTALS] = widgetJson.encodeToString<Map<String, Long>>(totals)
        if (drawingId == null) p.remove(WIDGET_CURRENT) else p[WIDGET_CURRENT] = "$drawingId|$nowMs"
    }

    /** 그림 id → 위젯에 떠 있던 시간(ms). 지금 떠 있는 그림은 [nowMs] 까지 더한다. */
    fun widgetShownMs(nowMs: () -> Long): Flow<Map<String, Long>> = store.data.map { p ->
        val totals = widgetJson.decodeFromString<Map<String, Long>>(p[WIDGET_TOTALS] ?: "{}").toMutableMap()
        p[WIDGET_CURRENT]?.split('|')?.takeIf { it.size == 2 }?.let { (id, since) ->
            since.toLongOrNull()?.let { totals[id] = (totals[id] ?: 0) + (nowMs() - it).coerceAtLeast(0) }
        }
        totals
    }

    /** 탈퇴: 이 기기 설정을 모두 지운다. 다음 실행은 처음 화면(로그인)부터. 기기 id 는 새로 만든다. */
    suspend fun reset() = store.edit { it.clear() }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ROLE = stringPreferencesKey("role")
        val THEME = stringPreferencesKey("theme_accent")
        val ONBOARDED = intPreferencesKey("onboarded")
        val KEY_EPOCH = intPreferencesKey("key_epoch")
        val MARKETING = intPreferencesKey("marketing_consent")
        val PROVIDER = stringPreferencesKey("account_provider")
        val LETTER_DRAFT = stringPreferencesKey("letter_draft")
        val WIDGET_PIN = stringPreferencesKey("widget_pin")
        val WIDGET_CURRENT = stringPreferencesKey("widget_current")
        val WIDGET_TOTALS = stringPreferencesKey("widget_totals")
        val widgetJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val NOTIFY_PREFS = stringPreferencesKey("notify_prefs")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val WIDGET_FRAME = stringPreferencesKey("widget_frame")
    }
}

/**
 * [DeviceSettings] 를 [Identity] 로. DI 그래프는 앱이 뜨자마자 만들어지지만 id 는 DataStore 를 읽어야 알 수 있으므로,
 * 앱 셸이 [DeviceSettings.ensureIdentity] 를 끝낸 뒤 [update] 로 채운다. 그 전에 화면은 그려지지 않는다.
 */
class IdentityHolder : Identity {
    private var state: DeviceState? = null

    fun update(s: DeviceState) {
        state = s
    }

    private val current get() = checkNotNull(state) { "신원을 아직 읽지 않았다" }

    override val userId: String get() = current.userId
    override val deviceId: String get() = current.deviceId
    override val keyEpoch: Int get() = current.keyEpoch
}
