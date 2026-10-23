package com.sadturtleman.flowershoes.core.widget

import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.StatusRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
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
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * 위젯이 그리는 값 전부. 위젯은 이 파일만 읽는다(설계 8장).
 * iOS NSE 도 작은 변경(보고싶어 횟수 · 상대 상태)은 이 파일에 직접 반영한다 — 필드를 바꿀 때 Swift 쪽도 함께 고친다.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int = 1,
    /** 기기 테마(위젯도 이 기기 테마를 따른다). */
    val theme: String,
    val daysLeft: Int? = null,
    val dischargeDate: String? = null,
    val progress: Float? = null,
    val rank: String? = null,
    val nextEventTitle: String? = null,
    val nextEventDays: Int? = null,
    val partnerStatus: String? = null,
    val missYouFromPartnerToday: Int = 0,
    val linked: Boolean = false,
    /** 받은 그림 위젯: 공개된(예약 편지 제외) 가장 최근 편지의 첫 그림. 문서 첨부 경로와 모양. */
    val drawingDocPath: String? = null,
    /** 완성 PNG 가 있으면 위젯은 이것을 그대로 쓴다(문서를 그 자리에서 그리지 않는다). */
    val drawingPngPath: String? = null,
    val drawingShape: String? = null,
    val drawingLetterId: String? = null,
    /** 떠 있는 그림 id(위젯에 떠 있던 시간을 센다). */
    val drawingId: String? = null,
    val updatedAtMs: Long,
)

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
        val partnerFlow = db.coupleDao().observe()
        partnerFlow.flatMapLatest { couple ->
            val partner = couple?.partnerUserId.orEmpty()
            combine(
                settings.state,
                profile.observeTimelines(),
                events.observeNext(),
                if (partner.isEmpty()) flowOf(null) else status.observeLatest(partner),
                if (partner.isEmpty()) flowOf(0) else missYou.observeTodayFrom(partner),
                // 예약 편지는 공개 시각이 지나야 위젯에 나온다 — 시계와 묶어 다시 묻는다.
                // 위젯에 다시 띄운 그림이 있으면 그것 — 그 뒤에 새 그림이 오면 새 그림.
                combine(
                    clock.ticker(60.seconds).flatMapLatest { now -> db.drawingDao().observeForWidget(identity.userId, now.toEpochMilliseconds()) },
                    settings.widgetPin.flatMapLatest { pin ->
                        if (pin == null) flowOf<Pair<com.sadturtleman.flowershoes.core.database.DrawingEntity, Long>?>(null)
                        else db.drawingDao().observe(pin.first).map { d -> d?.let { it to pin.second } }
                    },
                ) { latest: com.sadturtleman.flowershoes.core.database.DrawingEntity?, pinned: Pair<com.sadturtleman.flowershoes.core.database.DrawingEntity, Long>? ->
                    val (pin, at) = pinned ?: return@combine latest
                    val latestAt = latest?.let { it.receivedAtMs ?: it.sentAtMs } ?: 0L
                    if (latestAt > at) latest else pin
                },
            ) { values ->
                val device = values[0] as com.sadturtleman.flowershoes.core.data.DeviceState
                @Suppress("UNCHECKED_CAST")
                val timeline = (values[1] as List<Pair<*, com.sadturtleman.flowershoes.core.military.ServiceTimeline>>).firstOrNull()?.second
                val next = values[2] as com.sadturtleman.flowershoes.core.model.Event?
                val st = values[3] as com.sadturtleman.flowershoes.core.model.Status?
                val count = values[4] as Int
                val drawing = values[5] as com.sadturtleman.flowershoes.core.database.DrawingEntity?
                val today = clock.today()
                WidgetSnapshot(
                    theme = device.theme.name,
                    daysLeft = timeline?.daysLeft(today),
                    dischargeDate = timeline?.dischargeDate?.toString(),
                    progress = timeline?.progress(today)?.toFloat(),
                    rank = timeline?.rankAt(today)?.rank?.name,
                    nextEventTitle = next?.let { e -> e.title.ifBlank { e.type.koreanLabel() } },
                    nextEventDays = next?.let { today.daysUntil(it.start.date) },
                    partnerStatus = st?.label,
                    missYouFromPartnerToday = count,
                    linked = partner.isNotEmpty(),
                    drawingDocPath = drawing?.docAttachmentId?.let { (attachmentsRoot / it).toString() },
                    drawingPngPath = drawing?.takeIf { it.pngAttachmentId != it.docAttachmentId }?.let { (attachmentsRoot / it.pngAttachmentId).toString() },
                    drawingShape = drawing?.shape,
                    drawingLetterId = drawing?.letterId,
                    drawingId = drawing?.id,
                    updatedAtMs = 0,
                )
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
}

/** 위젯 · 알림 문구용 일정 이름(제목이 비었을 때). */
fun com.sadturtleman.flowershoes.core.model.EventType.koreanLabel(): String = when (this) {
    com.sadturtleman.flowershoes.core.model.EventType.VACATION -> "휴가"
    com.sadturtleman.flowershoes.core.model.EventType.OVERNIGHT -> "외박"
    com.sadturtleman.flowershoes.core.model.EventType.VISIT -> "면회"
    com.sadturtleman.flowershoes.core.model.EventType.TRAINING -> "훈련"
    com.sadturtleman.flowershoes.core.model.EventType.DUTY -> "당직"
}
