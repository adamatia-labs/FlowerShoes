package com.sadturtleman.flowershoes.core.widget

import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.StatusRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.seconds
import com.sadturtleman.flowershoes.core.database.AppDatabase
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * 위젯이 그리는 값 전부. 위젯은 이 파일만 읽는다(설계 8장). 글자는 [widgetSnapshot] 이 다 만들어 두고 위젯은 그대로 그린다
 * — Android · iOS 위젯이 같은 문구를 쓴다.
 * iOS NSE 도 작은 변경(보고싶어 횟수 · 상대 상태)은 이 파일에 직접 반영하고, 위젯의 보고싶어 App Intent 는 보낸 횟수를 올린다
 * — 필드를 바꿀 때 Swift 쪽(SharedStore.WidgetSnapshot)도 함께 고친다.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = 2,
    /** 기기 테마(위젯도 이 기기 테마를 따른다). */
    val theme: String,
    /** 전역 D-day: "민준이 전역까지" · "D-479" · "28.6% · 상병(예정) D-53". 입대일이 없으면 셋 다 null. */
    val ddayCaption: String? = null,
    val ddayValue: String? = null,
    val ddayFoot: String? = null,
    /** 복무 진행(복무 중 · 전역 당일만): 0.0 ~ 1.0 + "입대 2026.04.20" · "28.6%" · "전역 2028.02.19". */
    val progress: Float? = null,
    val progressStart: String? = null,
    val progressPercent: String? = null,
    val progressEnd: String? = null,
    /** 종이 태그(중간 · 큰 위젯): 다음 휴가 · 다음 만남(면회 · 외박) · 다음 진급 예정. 없으면 null. */
    val tagLeave: WidgetTag? = null,
    val tagMeet: WidgetTag? = null,
    val tagRank: WidgetTag? = null,
    /** 연인 이름(연결 전이면 null). */
    val partnerName: String? = null,
    /** 상대 상태: "민준은 지금" · "당직 중" · 색(ARGB, 상대가 고른 값) · "내일 08:00까지". */
    val partnerStatusCaption: String? = null,
    val partnerStatus: String? = null,
    val partnerStatusColor: Long? = null,
    val partnerStatusUntil: String? = null,
    /** 다음 만남(면회 · 외박 · 휴가 중 가장 가까운 것): 남은 날 · "D-11" · 일정 종류 · "11월 8일 면회" · "토요일 10:00". */
    val nextEventDays: Int? = null,
    val nextEventValue: String? = null,
    val nextEventType: String? = null,
    val nextEventLine: String? = null,
    val nextEventWhen: String? = null,
    /** 편지(큰 위젯): 안 읽은 편지 수, 줄 = "새 편지 1통"(없으면 가장 최근 편지 첫 줄) · "민준 · 오후 9:12". */
    val unreadLetters: Int = 0,
    val letterLine: String? = null,
    val letterMeta: String? = null,
    val letterId: String? = null,
    val missYouFromPartnerToday: Int = 0,
    /** 오늘 내가 보낸 보고싶어(위젯 "오늘 3번 보냈어요"). */
    val missYouSentToday: Int = 0,
    /** 방금(1분 안) 보낸 보고싶어 시각. 그 뒤엔 null — 위젯은 이 값이 있으면 "보냈어요" 상태로 그린다. */
    val missYouJustSentAtMs: Long? = null,
    val linked: Boolean = false,
    /** 받은 그림 위젯: 공개된(예약 편지 제외) 가장 최근 편지의 첫 그림. 문서 첨부 경로와 모양. */
    val drawingDocPath: String? = null,
    /** 완성 PNG 가 있으면 위젯은 이것을 그대로 쓴다(문서를 그 자리에서 그리지 않는다). */
    val drawingPngPath: String? = null,
    val drawingShape: String? = null,
    val drawingLetterId: String? = null,
    /** 떠 있는 그림 id(위젯에 떠 있던 시간을 센다). */
    val drawingId: String? = null,
    /** 그림 이름(보낸 사람이 붙인 것) · "민준 · 어제". */
    val drawingCaption: String? = null,
    val drawingMeta: String? = null,
    /** 받은 그림 위젯 프레임(BASIC · BLOSSOM · KRAFT · POLAROID · NIGHT). 없으면 기본. */
    val widgetFrame: String? = null,
    val updatedAtMs: Long,
)

/** 종이 태그 한 장: "다음 휴가" · "D-17". */
@Serializable
data class WidgetTag(val label: String, val value: String)

/** 스냅샷 파일. Android: filesDir/widget/snapshot.json, iOS: App Group 컨테이너. 원자적으로 바꿔 쓴다. */
class SnapshotStore(private val file: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun write(snapshot: WidgetSnapshot) {
        file.parent?.let { fs.createDirectories(it) }
        val tmp = file.parent!! / "${file.name}.tmp"
        fs.write(tmp) { writeUtf8(json.encodeToString(WidgetSnapshot.serializer(), snapshot)) }
        fs.atomicMove(tmp, file)
    }

    fun read(): WidgetSnapshot? = runCatching {
        if (!fs.exists(file)) null else json.decodeFromString(WidgetSnapshot.serializer(), fs.read(file) { readUtf8() })
    }.getOrNull()
}

/**
 * 앱이 살아 있는 동안 DB 변화를 지켜보다가 스냅샷을 다시 쓰고 [onUpdated] 로 위젯 갱신을 요청한다.
 * 연달아 바뀌면 0.5초 모아서 한 번만 쓴다.
 */
class WidgetSnapshotPublisher(
    private val db: AppDatabase,
    private val identity: Identity,
    private val settings: DeviceSettings,
    private val profile: ProfileRepository,
    private val events: EventRepository,
    private val status: StatusRepository,
    private val missYou: MissYouRepository,
    private val store: SnapshotStore,
    private val attachmentsRoot: Path,
    private val clock: Clock = Clock.System,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    /** [drawingWidgetPlaced]: 홈 화면에 그림 위젯이 있는지 — 없으면 "위젯에 표시됨" 시간을 세지 않는다. */
    fun start(scope: CoroutineScope, drawingWidgetPlaced: suspend () -> Boolean = { true }, onUpdated: suspend () -> Unit): Job = scope.launch {
        val me = identity.userId
        // 날이 바뀌면 "오늘" 기준 흐름(오늘 보고싶어 · 다음 일정)을 다시 묻는다.
        val days = clock.ticker(60.seconds).map { clock.today() }.distinctUntilChanged()
        db.coupleDao().observe().flatMapLatest { couple ->
            val partner = couple?.partnerUserId.orEmpty()
            days.flatMapLatest { today ->
                combine(
                    settings.state,
                    db.userDao().observeAll(),
                    profile.observeTimelines(),
                    events.observeRange(today, today.plus(DatePeriod(years = 2))),
                    if (partner.isEmpty()) flowOf(null) else status.observeLatest(partner),
                    if (partner.isEmpty()) flowOf(0) else missYou.observeTodayFrom(partner),
                    missYou.observeTodayFrom(me),
                    missYou.observeMyLastSentAt(),
                    db.letterDao().observeAll(),
                    db.receiptDao().observeIds(ReceiptEntity.LETTER.name, me),
                    drawingFlow(),
                    settings.widgetFrame,
                    // 1분마다 다시 만든다: 예약 편지 공개 · "보냈어요" 상태가 끝나는 때.
                    clock.ticker(60.seconds),
                ) { values ->
                    val device = values[0] as com.sadturtleman.flowershoes.core.data.DeviceState
                    @Suppress("UNCHECKED_CAST")
                    val users = values[1] as List<com.sadturtleman.flowershoes.core.database.UserEntity>
                    @Suppress("UNCHECKED_CAST")
                    val timeline = (values[2] as List<Pair<*, com.sadturtleman.flowershoes.core.military.ServiceTimeline>>).firstOrNull()?.second
                    @Suppress("UNCHECKED_CAST")
                    val upcoming = values[3] as List<com.sadturtleman.flowershoes.core.model.Event>
                    @Suppress("UNCHECKED_CAST")
                    val letters = values[8] as List<com.sadturtleman.flowershoes.core.database.LetterEntity>
                    @Suppress("UNCHECKED_CAST")
                    val read = (values[9] as List<String>).toSet()
                    val now = clock.now()
                    val mine = users.firstOrNull { it.id == me }
                    widgetSnapshot(
                        WidgetInputs(
                            theme = device.theme.name,
                            role = mine?.role?.let { r -> com.sadturtleman.flowershoes.core.model.Role.entries.firstOrNull { it.name == r } },
                            myName = mine?.nickname.orEmpty(),
                            partnerId = partner.ifEmpty { null },
                            partnerName = users.firstOrNull { it.id == partner }?.nickname,
                            timeline = timeline,
                            events = upcoming,
                            status = values[4] as com.sadturtleman.flowershoes.core.model.Status?,
                            missYouFromPartnerToday = values[5] as Int,
                            missYouSentToday = values[6] as Int,
                            missYouLastSentAtMs = values[7] as Long?,
                            letters = letters.filter { it.authorId == partner && it.visibleAtMs <= now.toEpochMilliseconds() }.map { it.toModel() },
                            readLetterIds = read,
                            drawing = values[10] as com.sadturtleman.flowershoes.core.database.DrawingEntity?,
                            widgetFrame = values[11] as String,
                        ),
                        attachmentsRoot, now,
                    )
                }
            }
        }
            .distinctUntilChanged()
            .debounce(500)
            .collect { snap ->
                store.write(snap.copy(updatedAtMs = clock.now().toEpochMilliseconds()))
                onUpdated()
                val placed = runCatching { drawingWidgetPlaced() }.getOrDefault(false)
                settings.widgetShowing(snap.drawingId?.takeIf { placed }, clock.now().toEpochMilliseconds())
            }
    }

    /** 위젯에 다시 띄운 그림이 있으면 그것 — 그 뒤에 새 그림이 오면 새 그림. 예약 편지는 공개 시각이 지나야 나온다(시계와 묶어 다시 묻는다). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun drawingFlow() = combine(
        clock.ticker(60.seconds).flatMapLatest { now -> db.drawingDao().observeForWidget(identity.userId, now.toEpochMilliseconds()) },
        settings.widgetPin.flatMapLatest { pin ->
            if (pin == null) flowOf<Pair<com.sadturtleman.flowershoes.core.database.DrawingEntity, Long>?>(null)
            else db.drawingDao().observe(pin.first).map { d -> d?.let { it to pin.second } }
        },
    ) { latest: com.sadturtleman.flowershoes.core.database.DrawingEntity?, pinned: Pair<com.sadturtleman.flowershoes.core.database.DrawingEntity, Long>? ->
        val (pin, at) = pinned ?: return@combine latest
        val latestAt = latest?.let { it.receivedAtMs ?: it.sentAtMs } ?: 0L
        if (latestAt > at) latest else pin
    }
}

/** 위젯 · 알림 문구용 일정 이름(제목이 비었을 때). */
fun com.sadturtleman.flowershoes.core.model.EventType.koreanLabel(): String = when (this) {
    com.sadturtleman.flowershoes.core.model.EventType.VACATION -> "휴가"
    com.sadturtleman.flowershoes.core.model.EventType.OVERNIGHT -> "외박"
    com.sadturtleman.flowershoes.core.model.EventType.VISIT -> "면회"
    com.sadturtleman.flowershoes.core.model.EventType.TRAINING -> "훈련"
    com.sadturtleman.flowershoes.core.model.EventType.DUTY -> "당직"
    com.sadturtleman.flowershoes.core.model.EventType.ANNIVERSARY -> "기념일"
    com.sadturtleman.flowershoes.core.model.EventType.OTHER -> "기타"
}
