package com.sadturtleman.flowershoes.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sadturtleman.flowershoes.core.data.sync.PendingInvite
import com.sadturtleman.flowershoes.core.data.sync.PendingInviteStore
import com.sadturtleman.flowershoes.core.data.sync.PendingAcceptance
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 이 기기의 설정. 온보딩이 어디까지 왔는지 · 테마 · 로컬 신원. */
data class DeviceState(
    val userId: String,
    val deviceId: String,
    val role: Role?,
    val theme: AppTheme,
    val onboarded: Boolean,
    val keyEpoch: Int,
    val accountSession: String = "",
)

/**
 * 이 기기에만 저장하는 값(DataStore). 동기화하지 않는다 — 테마를 바꿔도 상대 화면은 그대로다.
 *
 * 로그인(:core:auth)이 붙기 전에는 첫 실행 때 만든 로컬 id 를 쓴다. 로그인하면 [bindAccount] 로 uid 에 묶는다.
 */
class DeviceSettings(private val store: DataStore<Preferences>) {

    /** 화면 전용 설정도 bind/reset과 같은 실제 DataStore 갱신 경계에서 소유자를 확인한다. */
    fun forScreen(session: ScreenWriteSession): DeviceSettings = DeviceSettings(object : DataStore<Preferences> {
        override val data = store.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = store.updateData { p ->
            if (p.draftSession() != session.owner) throw ScreenSessionChangedException()
            withContext(AccountWriteBoundary(session.owner)) { session.transaction { transform(p) } }
        }
    })

    val state: Flow<DeviceState> = store.data.map { p ->
        DeviceState(
            userId = p[USER_ID].orEmpty(),
            deviceId = p[DEVICE_ID].orEmpty(),
            role = p[ROLE]?.let(Role::valueOf),
            theme = AppTheme.from(p[THEME], p[ROLE]?.let(Role::valueOf)),
            onboarded = p[ONBOARDED] == 1,
            keyEpoch = p[KEY_EPOCH] ?: 0,
            accountSession = p[ACCOUNT_SESSION].orEmpty(),
        )
    }

    /** 첫 실행이면 로컬 id 를 만든다. 언제 불러도 같은 값을 돌려준다. */
    suspend fun ensureIdentity(): DeviceState {
        store.edit { p ->
            if (p[USER_ID] == null) p[USER_ID] = "local:${Uuid.random()}"
            if (p[DEVICE_ID] == null) p[DEVICE_ID] = Uuid.random().toString()
            if (p[ACCOUNT_SESSION] == null) p[ACCOUNT_SESSION] = Uuid.random().toString()
            p[DRAFT_GENERATIONS] = prefsJson.encodeToString<Map<String, String>>(p.draftGenerations() + (checkNotNull(p[USER_ID]) to checkNotNull(p[ACCOUNT_SESSION])))
            if (p[FIRST_RUN] == null) p[FIRST_RUN] = kotlin.time.Clock.System.now().toEpochMilliseconds()
        }
        return state.first()
    }

    /** [provider] 는 로그인 방법(KAKAO · APPLE · GOOGLE) — 복원 · 설정 › 로그인 계정 화면에 보인다. */
    suspend fun bindAccount(uid: String, provider: String? = null) = store.edit {
        check(it[CLEANUP_TOKEN] == null || it[USER_ID] == uid) { "진행 중인 연결 정리를 먼저 마쳐 주세요" }
        if (it[USER_ID] != uid || it[ACCOUNT_SESSION] == null) it[ACCOUNT_SESSION] = Uuid.random().toString()
        it[USER_ID] = uid
        it[DRAFT_GENERATIONS] = prefsJson.encodeToString<Map<String, String>>(it.draftGenerations() + (uid to checkNotNull(it[ACCOUNT_SESSION])))
        if (provider != null) it[PROVIDER] = provider
    }

    val accountProvider: Flow<String?> = store.data.map { it[PROVIDER] }

    val draftSessions: Flow<DraftSession?> = store.data.map { it.draftSession() }

    /** 화면을 열었을 때의 계정 세대와 실제 쓰기를 같은 설정 잠금에서 확인한다. */
    internal suspend fun withCurrentAccount(expected: DeviceState, block: suspend () -> Unit): Boolean {
        var applied = false
        store.edit { p ->
            if (p[USER_ID] == expected.userId && p[DEVICE_ID] == expected.deviceId &&
                p[ACCOUNT_SESSION] == expected.accountSession && p[ACCOUNT_CLEANUP] == null && p[CLEANUP_TOKEN] == null) {
                withContext(AccountWriteBoundary(DraftSession(expected.userId, expected.deviceId, expected.accountSession))) { block() }
                applied = true
            }
        }
        return applied
    }

    private fun Preferences.draftGenerations(): Map<String, String> =
        prefsJson.decodeFromString<Map<String, String>>(this[DRAFT_GENERATIONS] ?: "{}")

    internal suspend fun draftSessionForCleanup(): DraftSession? = store.data.first().draftSession(includeCleaning = true)

    private fun Preferences.draftSession(includeCleaning: Boolean = false): DraftSession? {
        if (!includeCleaning && (this[ACCOUNT_CLEANUP] != null || this[CLEANUP_TOKEN] != null)) return null
        val uid = this[USER_ID]?.takeIf { it.isNotBlank() } ?: return null
        val device = this[DEVICE_ID]?.takeIf { it.isNotBlank() } ?: return null
        val generation = this[ACCOUNT_SESSION]?.takeIf { it.isNotBlank() } ?: return null
        return DraftSession(uid, device, generation)
    }

    /** 신원 캐시 대신 bind/reset과 같은 저장 경계에서 확인한다. block은 설정에 다시 접근하지 않는다. */
    internal suspend fun withDraftSession(expected: DraftSession, finishCapturedEdit: Boolean = false, invalidate: Boolean = false, includeCleaning: Boolean = false, block: suspend () -> Unit): Boolean {
        var applied = false
        store.edit { p ->
            val current = p.draftSession(includeCleaning)
            val inactiveOwner = finishCapturedEdit && current != null && current.deviceId == expected.deviceId && current.ownerUid != expected.ownerUid &&
                p.draftGenerations()[expected.ownerUid] == expected.generation
            if (current == expected || inactiveOwner) {
                withContext(AccountWriteBoundary(checkNotNull(current))) { block() }; applied = true
                if (invalidate) {
                    val generation = Uuid.random().toString()
                    p[ACCOUNT_SESSION] = generation
                    p[DRAFT_GENERATIONS] = prefsJson.encodeToString<Map<String, String>>(p.draftGenerations() + (expected.ownerUid to generation))
                }
            }
        }
        return applied
    }

    suspend fun setRole(role: Role) = store.edit {
        it[ROLE] = role.name
        // 테마를 아직 안 골랐으면 역할 기본값을 미리 넣어 둔다(곰신은 곰신 테마, 군인은 군인 테마).
        if (it[THEME] == null) it[THEME] = AppTheme.defaultFor(role).name
    }

    suspend fun setTheme(theme: AppTheme) = store.edit { it[THEME] = theme.name }

    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = 1 }

    /** 약관 동의(온보딩 0-1)의 선택 항목: 이벤트 · 혜택 알림. 필수 항목은 동의해야 시작할 수 있어 따로 두지 않는다. */
    suspend fun setMarketingConsent(agreed: Boolean) = store.edit { it[MARKETING] = if (agreed) 1 else 0 }

    val marketingConsent: kotlinx.coroutines.flow.Flow<Boolean> = store.data.map { (it[MARKETING] ?: 0) == 1 }

    suspend fun setKeyEpoch(epoch: Int) = store.edit { it[KEY_EPOCH] = epoch }

    internal val cleanupRequests = store.data.map { p -> p[CLEANUP_REQUEST]?.let {
        prefsJson.decodeFromString(com.sadturtleman.flowershoes.core.database.CleanupEntity.serializer(), it)
    } }

    internal suspend fun cleanupRequest() = cleanupRequests.first()

    /** 아직 서버 요청/Room 기록이 없고 대상이 교체된 예약만 취소한다. */
    internal suspend fun cancelCleanupReservation(token: String, canRelease: suspend () -> Boolean) {
        store.edit { p ->
            if (p[CLEANUP_TOKEN] == token && p[CLEANUP_FINISHED] == null && canRelease()) {
                p.remove(CLEANUP_REQUEST)
                p.remove(CLEANUP_TOKEN)
            }
        }
    }

    /** Room 기록 전에 요청 전체와 잠금을 먼저 커밋한다. 중간 종료 시 같은 요청을 Room에 복구한다. */
    internal suspend fun reserveCleanup(cleanup: com.sadturtleman.flowershoes.core.database.CleanupEntity) {
        store.edit { p ->
            check(p[CLEANUP_TOKEN] == null && p[USER_ID].orEmpty() == cleanup.ownerUid && p[DEVICE_ID].orEmpty() == cleanup.deviceId &&
                p[ACCOUNT_SESSION].orEmpty() == cleanup.accountSession) { "로그인 또는 진행 중인 정리가 바뀌었어요" }
            check(cleanup.source == "ACCOUNT" || p.acceptances().none { it.ownerUid == cleanup.ownerUid && it.deviceId == cleanup.deviceId }) { "진행 중인 연결을 먼저 확인해 주세요" }
            p[CLEANUP_REQUEST] = prefsJson.encodeToString(com.sadturtleman.flowershoes.core.database.CleanupEntity.serializer(), cleanup)
            p[CLEANUP_TOKEN] = cleanup.token
        }
    }

    /** Room 쓰기보다 먼저 설정 잠금을 잡는다. block은 설정에 재진입하지 않는다. */
    internal suspend fun withCleanupIdentity(cleanup: com.sadturtleman.flowershoes.core.database.CleanupEntity, block: suspend () -> Unit) {
        store.edit { p ->
            check(p[USER_ID].orEmpty() == cleanup.ownerUid && p[DEVICE_ID].orEmpty() == cleanup.deviceId &&
                p[ACCOUNT_SESSION].orEmpty() == cleanup.accountSession &&
                p[CLEANUP_TOKEN] == cleanup.token) { "로그인한 계정이 바뀌었어요" }
            block()
            p[CLEANUP_TOKEN] = cleanup.token
        }
    }

    internal suspend fun cleanupSettingsFinished(token: String): Boolean = store.data.first()[CLEANUP_FINISHED] == token

    /** 완료 표식은 Room 작업을 지울 때까지 남긴다. 설정 저장 실패도 같은 단계로 재시도한다. */
    internal suspend fun finishCleanupSettings(cleanup: com.sadturtleman.flowershoes.core.database.CleanupEntity, checkCurrent: suspend () -> Unit) {
        store.edit { p ->
            if (p[CLEANUP_FINISHED] == cleanup.token) return@edit
            check(p[USER_ID].orEmpty() == cleanup.ownerUid && p[DEVICE_ID].orEmpty() == cleanup.deviceId &&
                p[ACCOUNT_SESSION].orEmpty() == cleanup.accountSession && p[CLEANUP_TOKEN] == cleanup.token) { "로그인한 계정이 바뀌었어요" }
            checkCurrent()
            if (cleanup.source == "ACCOUNT") {
                p.clear()
                p[FAREWELL] = 1
                if (cleanup.appleInstructions) p[FAREWELL_APPLE] = 1
            } else {
                p[KEY_EPOCH] = 0
                val generation = Uuid.random().toString()
                p[ACCOUNT_SESSION] = generation
                p[DRAFT_GENERATIONS] = prefsJson.encodeToString<Map<String, String>>(p.draftGenerations() + (cleanup.ownerUid to generation))
                p.remove(PENDING_INVITE)
                p.remove(PENDING_ACCEPTANCES)
            }
            // Room 삭제 뒤 마지막 설정 저장이 실패해도 복구 안내/원래 선택을 남긴다.
            p[CLEANUP_REQUEST] = prefsJson.encodeToString(com.sadturtleman.flowershoes.core.database.CleanupEntity.serializer(), cleanup)
            p[CLEANUP_TOKEN] = cleanup.token
            p[CLEANUP_FINISHED] = cleanup.token
        }
    }

    internal suspend fun releaseCleanup(token: String, clearJournal: suspend () -> Unit) {
        store.edit { p ->
            check(p[CLEANUP_TOKEN] == token && p[CLEANUP_FINISHED] == token)
            clearJournal()
            p.remove(CLEANUP_TOKEN)
            p.remove(CLEANUP_FINISHED)
            p.remove(CLEANUP_REQUEST)
        }
    }

    /** Room 삭제 후 설정 저장만 실패한 경우도 다음 실행에서 마지막 표식을 지운다. */
    internal suspend fun releaseFinishedCleanupIf(noJournal: suspend () -> Boolean) {
        store.edit { p ->
            val token = p[CLEANUP_TOKEN]
            if (token != null && p[CLEANUP_FINISHED] == token && noJournal()) {
                p.remove(CLEANUP_TOKEN)
                p.remove(CLEANUP_FINISHED)
                p.remove(CLEANUP_REQUEST)
            }
        }
    }

    private val prefsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** 알림 설정(이 기기). 서버에 올리는 것은 [NotifyPrefsTransport] 가 따로 한다. */
    val notifyPrefs: Flow<NotificationPrefs> = store.data.map { p ->
        p[NOTIFY_PREFS]?.let { runCatching { prefsJson.decodeFromString(NotificationPrefs.serializer(), it) }.getOrNull() } ?: NotificationPrefs()
    }

    suspend fun setNotifyPrefs(prefs: NotificationPrefs, writePreview: () -> Unit = {}) = store.edit {
        // iOS 확장의 미리보기 파일도 화면의 계정/관계 검사를 통과한 같은 쓰기 경계 안에서 바꾼다.
        writePreview()
        it[NOTIFY_PREFS] = prefsJson.encodeToString(NotificationPrefs.serializer(), prefs)
    }

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

    /** 기다리는 초대 링크(홈 "초대 보냄 · 기다리는 중"). 연결되거나 만료되면 null. */
    val pendingInvite: Flow<PendingInvite?> = store.data.map { p ->
        p.pendingInvite()?.takeIf { it.ownerUid == p[USER_ID] && it.deviceId == p[DEVICE_ID] }
    }

    private fun Preferences.pendingInvite(): PendingInvite? {
        val saved = this[PENDING_INVITE] ?: return null
        if (saved.startsWith("{")) return prefsJson.decodeFromString<PendingInvite>(saved)
        // 구버전에는 소유자가 없다. 원문을 보존하되 현재 계정에 임의로 귀속하지 않는다.
        return saved.split('|').takeIf { it.size == 2 }?.let { (t, at) -> at.toLongOrNull()?.let { PendingInvite(t, it) } }
    }

    private fun Preferences.acceptances(): List<PendingAcceptance> =
        this[PENDING_ACCEPTANCES]?.let { prefsJson.decodeFromString<List<PendingAcceptance>>(it) }.orEmpty()

    /** 보낸 초대 링크(토큰 · 만료). 앱을 다시 켜도 수락을 이어 받는다 — [com.sadturtleman.flowershoes.core.data.sync.PairingSession]. */
    val pendingInvites: PendingInviteStore = object : PendingInviteStore {
        override suspend fun load(): PendingInvite? = store.data.first().pendingInvite()

        override suspend fun save(invite: PendingInvite?) {
            store.edit { p ->
                if (invite == null) p.remove(PENDING_INVITE) else {
                    check(p[CLEANUP_TOKEN] == null) { "진행 중인 연결 정리를 먼저 마쳐 주세요" }
                    check(invite.ownerUid == p[USER_ID] && invite.deviceId == p[DEVICE_ID]) { "로그인한 계정이 바뀌었어요" }
                    p[PENDING_INVITE] = prefsJson.encodeToString(PendingInvite.serializer(), invite)
                }
            }
        }

        override suspend fun isCurrentIdentity(uid: String, deviceId: String): Boolean =
            store.data.first().let { it[USER_ID] == uid && it[DEVICE_ID] == deviceId && it[CLEANUP_TOKEN] == null }

        override suspend fun withCurrentIdentity(uid: String, deviceId: String, block: suspend () -> Unit): Boolean {
            var applied = false
            // bindAccount/reset과 같은 DataStore 갱신 경계에서 검사한다. block은 설정을 다시 쓰지 않는다.
            store.edit { p ->
                if (p[USER_ID] == uid && p[DEVICE_ID] == deviceId && p[CLEANUP_TOKEN] == null) {
                    block()
                    applied = true
                }
            }
            return applied
        }

        override suspend fun loadAcceptance(uid: String, deviceId: String): PendingAcceptance? = store.data.first().let { p ->
            if (p[USER_ID] != uid || p[DEVICE_ID] != deviceId) null
            else p.acceptances().firstOrNull { it.ownerUid == uid && it.deviceId == deviceId }
        }

        override suspend fun saveAcceptance(acceptance: PendingAcceptance) {
            store.edit { p ->
                check(p[CLEANUP_TOKEN] == null) { "진행 중인 연결 정리를 먼저 마쳐 주세요" }
                check(p[USER_ID] == acceptance.ownerUid && p[DEVICE_ID] == acceptance.deviceId) { "로그인한 계정이 바뀌었어요" }
                val saved = p.acceptances()
                val old = saved.firstOrNull { it.ownerUid == acceptance.ownerUid && it.deviceId == acceptance.deviceId }
                check(old == null || old == acceptance) { "먼저 진행 중인 연결을 확인해 주세요" }
                p[PENDING_ACCEPTANCES] = prefsJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(PendingAcceptance.serializer()), saved.filterNot { it == old } + acceptance)
            }
        }

        override suspend fun clearAcceptance(expected: PendingAcceptance) {
            store.edit { p ->
                if (p[USER_ID] == expected.ownerUid && p[DEVICE_ID] == expected.deviceId) {
                    p[PENDING_ACCEPTANCES] = prefsJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(PendingAcceptance.serializer()), p.acceptances().filterNot { it == expected })
                }
            }
        }

        override suspend fun clearSent(uid: String, deviceId: String) {
            store.edit { p ->
                val saved = p.pendingInvite()
                if (p[USER_ID] == uid && p[DEVICE_ID] == deviceId && saved?.ownerUid == uid && saved.deviceId == deviceId) p.remove(PENDING_INVITE)
            }
        }
    }

    /**
     * 처음 연결 직후 "훈련소 동안 쓴 편지 N통 도착"을 기다리는 중(이 기기에만). 첫 연결이 켜고, 띄우면 끈다 —
     * 다시 연결(새 폰)은 켜지 않아 넘겨받은 옛 편지로 뜨지 않는다.
     */
    val soloArrivalWatch: Flow<Boolean> = store.data.map { it[SOLO_ARRIVAL] == 1 }

    suspend fun setSoloArrivalWatch(on: Boolean) = store.edit { if (on) it[SOLO_ARRIVAL] = 1 else it.remove(SOLO_ARRIVAL) }

    /** 홈의 그날의 한 줄 카드를 닫은 일정(이 기기에만). 카드는 사흘이면 어차피 사라지니 최근 것만 둔다. */
    val dayLineCards: DayLineCards = object : DayLineCards {
        override val dismissed: Flow<Set<String>> = store.data.map { p -> p[DAYLINE_DISMISSED]?.split('|')?.filter { it.isNotEmpty() }?.toSet().orEmpty() }

        override suspend fun dismiss(eventId: String) {
            store.edit { p ->
                val ids = p[DAYLINE_DISMISSED]?.split('|')?.filter { it.isNotEmpty() && it != eventId }.orEmpty()
                p[DAYLINE_DISMISSED] = (ids + eventId).takeLast(DAYLINE_KEEP).joinToString("|")
            }
        }
    }

    /** 추억 책 권유 팝업(이 기기에만): 본 날 · 다시 보지 않기. 상대 폰은 따로 한 번씩 본다. */
    val bookPromo: BookPromoStore = object : BookPromoStore {
        override val seen: Flow<BookPromoSeen> = store.data.map { p ->
            val s = p[BOOK_PROMO]?.split('|').orEmpty().toSet()
            BookPromoSeen(d30 = BookPromoDay.D30.name in s, discharge = BookPromoDay.DISCHARGE.name in s, never = PROMO_NEVER in s)
        }

        override suspend fun markSeen(day: BookPromoDay) = add(day.name)

        override suspend fun never() = add(PROMO_NEVER)

        private suspend fun add(flag: String) {
            store.edit { p -> p[BOOK_PROMO] = (p[BOOK_PROMO]?.split('|').orEmpty().filter { it.isNotEmpty() }.toSet() + flag).joinToString("|") }
        }
    }

    /** 내 기록 내보내기 기록(3개월마다 알림의 기준). 끄면 알림 설정이 null 로 거른다. */
    val exportHistory: Flow<ExportHistory> = store.data.map { ExportHistory(it[FIRST_RUN], it[LAST_EXPORT]) }

    /** 내 기록 ZIP 을 만들어 공유 시트로 넘겼다. */
    suspend fun markExported(atMs: Long) = store.edit { it[LAST_EXPORT] = atMs }

    /**
     * 탈퇴: 이 기기 설정을 모두 지운다. 다음 실행은 처음 화면(로그인)부터. 기기 id 는 새로 만든다.
     * 같은 쓰기에서 [farewellPending] 을 세운다 — 처음 화면이 탈퇴 완료 화면을 먼저 한 번 보인다(앱이 꺼져도 남는다).
     */
    suspend fun reset(showAppleInstructions: Boolean = false) = store.edit {
        it.clear()
        it[FAREWELL] = 1
        if (showAppleInstructions) it[FAREWELL_APPLE] = 1
    }

    /** null: 서버 삭제 미확인, 그 외: 서버 삭제 완료 후 남은 로컬 정리와 Apple 안내 여부. */
    val pendingAccountCleanup: Flow<Boolean?> = store.data.map { it[ACCOUNT_CLEANUP]?.let { value -> value == 1 } }

    suspend fun setPendingAccountCleanup(showAppleInstructions: Boolean) = store.edit {
        it[ACCOUNT_CLEANUP] = if (showAppleInstructions) 1 else 0
    }

    /** 탈퇴 완료 화면("그동안 고마웠어요")을 아직 보이지 않았다. */
    val farewellPending: Flow<Boolean> = store.data.map { it[FAREWELL] == 1 }

    val farewellAppleInstructions: Flow<Boolean> = store.data.map { it[FAREWELL_APPLE] == 1 }

    suspend fun setFarewellPending(on: Boolean) = store.edit { if (on) it[FAREWELL] = 1 else { it.remove(FAREWELL); it.remove(FAREWELL_APPLE) } }

    private companion object {
        val CLEANUP_TOKEN = stringPreferencesKey("relationship_cleanup_token")
        val CLEANUP_REQUEST = stringPreferencesKey("relationship_cleanup_request")
        val CLEANUP_FINISHED = stringPreferencesKey("relationship_cleanup_finished")
        val PENDING_ACCEPTANCES = stringPreferencesKey("pending_received_invites")
        val ACCOUNT_CLEANUP = intPreferencesKey("pending_account_cleanup")
        val FAREWELL = intPreferencesKey("farewell_pending")
        val FAREWELL_APPLE = intPreferencesKey("farewell_apple_manual")
        val USER_ID = stringPreferencesKey("user_id")
        val ACCOUNT_SESSION = stringPreferencesKey("account_session")
        val DRAFT_GENERATIONS = stringPreferencesKey("draft_account_sessions")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ROLE = stringPreferencesKey("role")
        val THEME = stringPreferencesKey("theme_accent")
        val ONBOARDED = intPreferencesKey("onboarded")
        val KEY_EPOCH = intPreferencesKey("key_epoch")
        val MARKETING = intPreferencesKey("marketing_consent")
        val PROVIDER = stringPreferencesKey("account_provider")
        val WIDGET_PIN = stringPreferencesKey("widget_pin")
        val WIDGET_CURRENT = stringPreferencesKey("widget_current")
        val WIDGET_TOTALS = stringPreferencesKey("widget_totals")
        val widgetJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val NOTIFY_PREFS = stringPreferencesKey("notify_prefs")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val WIDGET_FRAME = stringPreferencesKey("widget_frame")
        val PENDING_INVITE = stringPreferencesKey("pending_invite")
        val DAYLINE_DISMISSED = stringPreferencesKey("dayline_dismissed")
        val SOLO_ARRIVAL = intPreferencesKey("solo_arrival_watch")
        const val DAYLINE_KEEP = 20
        val BOOK_PROMO = stringPreferencesKey("book_promo")
        val FIRST_RUN = longPreferencesKey("first_run_at")
        val LAST_EXPORT = longPreferencesKey("last_export_at")
        const val PROMO_NEVER = "NEVER"
    }
}

/** 이 폰을 처음 쓴 때 · 마지막으로 내 기록을 내보낸 때(ms). 내보낸 적이 없으면 처음 쓴 때부터 센다. */
data class ExportHistory(val firstRunAtMs: Long? = null, val lastExportAtMs: Long? = null)

/** 홈의 그날의 한 줄 카드에서 닫은 일정 id. */
interface DayLineCards {
    val dismissed: Flow<Set<String>>
    suspend fun dismiss(eventId: String)
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
