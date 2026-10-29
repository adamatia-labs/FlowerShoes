package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.BookCoverEntity
import com.sadturtleman.flowershoes.core.database.DrawingEntity
import com.sadturtleman.flowershoes.core.database.MemoryEntity
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.BookCover
import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.hasDayLine
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.MissYou
import com.sadturtleman.flowershoes.core.model.Receipt
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.Status
import com.sadturtleman.flowershoes.core.model.koreanName
import com.sadturtleman.flowershoes.core.sync.OpKind
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/** 한국 대상 앱이라 날짜 경계는 항상 서울 기준이다. */
val SEOUL: TimeZone = TimeZone.of("Asia/Seoul")

fun Clock.today(): LocalDate = todayIn(SEOUL)

/** 지금 시각을 [period] 마다 다시 내보낸다. 예약 편지 공개 · 상태 만료처럼 시간이 흐르면 바뀌는 화면에 쓴다. */
fun Clock.ticker(period: Duration = 30.seconds): Flow<Instant> = flow {
    while (true) {
        emit(now())
        delay(period)
    }
}

/** 내 정보 · 복무 정보 · 기념일. */
class ProfileRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val calculator: ServiceCalculator = ServiceCalculator(),
    private val clock: Clock = Clock.System,
) {
    fun observeUsers(): Flow<List<UserEntity>> = db.userDao().observeAll()

    fun myUserId(): String = identity.userId

    /** 연결된 연인의 uid. 연결 전 · 해제 뒤에는 null. */
    fun observePartnerId(): Flow<String?> = db.coupleDao().observe().map { it?.partnerUserId }

    /** 커플(연결). 혼자 모드면 null 이거나 상대가 비어 있다. */
    fun observeCouple(): Flow<com.sadturtleman.flowershoes.core.database.CoupleEntity?> = db.coupleDao().observe()

    /** 커플 중 군인의 복무 일정. 혼자 모드에서 곰신이 입력한 값이어도 여기서 보인다. */
    fun observeTimelines(): Flow<List<Pair<Service, ServiceTimeline>>> =
        db.serviceDao().observeAll().map { list -> list.map { it.toModel() }.map { it to calculator.timeline(it) } }

    /** 내 역할 · 이름. 연결돼 있으면 상대 폰에도 간다(이름은 "민준 ♥ 지수" · 편지 · 알림에 쓰인다). */
    suspend fun saveMe(role: Role, nickname: String, deviceName: String? = null, perks: Set<String>? = null) {
        val local = db.userDao().get(identity.userId)
        val device = deviceName ?: local?.deviceName.orEmpty()
        val bought = perks?.sorted() ?: local?.perks?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        writer.write(
            OpKind.PROFILE, "user", identity.userId,
            localWrite = { lamport -> db.userDao().upsert(UserEntity(identity.userId, role.name, nickname, lamport, device.ifBlank { null }, bought.joinToString(",").ifBlank { null })) },
            payload = { lamport -> OpPayload.UserPut(identity.userId, role.name, nickname, lamport, device, bought) },
        )
    }

    /** 산 것(꽃신 평생 · 추억 책)을 내 정보에 싣는다(바뀌었을 때만) — 연결된 상대도 광고 없이 · 꾸미기 · 책을 함께 쓴다. */
    suspend fun publishPerks(perks: Set<String>) {
        val me = db.userDao().get(identity.userId) ?: return
        if (me.perks?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty() == perks) return
        saveMe(Role.valueOf(me.role), me.nickname, perks = perks)
    }

    /** 연결된 상대가 산 것. 연결 전 · 해제 뒤에는 빈 집합. */
    fun observePartnerPerks(): Flow<Set<String>> = kotlinx.coroutines.flow.combine(observePartnerId(), observeUsers()) { partner, users ->
        partner?.let { id -> users.firstOrNull { it.id == id }?.perks?.split(',')?.filter { it.isNotBlank() }?.toSet() }.orEmpty()
    }

    /** 이 폰 기종을 내 정보에 싣는다(바뀌었을 때만). 연인의 설정 › 연결 정보에 "민준 폰 Galaxy S25"로 보인다. */
    suspend fun publishDevice(deviceName: String) {
        if (deviceName.isBlank()) return
        val me = db.userDao().get(identity.userId) ?: return
        if (me.deviceName == deviceName) return
        saveMe(Role.valueOf(me.role), me.nickname, deviceName)
    }

    suspend fun saveService(service: Service) {
        writer.write(
            OpKind.PROFILE, "service", service.userId,
            localWrite = { lamport -> db.serviceDao().upsert(service.copy(lamport = lamport).toEntity()) },
            payload = { lamport -> OpPayload.ServicePut(service.copy(lamport = lamport)) },
        )
    }

    /** 커플 기념일(함께한 날의 1일째). 기념일 일정([Event.COUPLE_ANNIVERSARY_ID])의 날짜다. */
    fun observeAnniversary(): Flow<LocalDate?> =
        db.eventDao().observe(Event.COUPLE_ANNIVERSARY_ID).map { e -> e?.toModel()?.takeIf { it.type == EventType.ANNIVERSARY }?.start?.date }
            .distinctUntilChanged()

    /**
     * 커플 기념일을 매년 반복 기념일 일정 하나로 남긴다(조용한 종류 — 알림 없이 반영). null 이면 그 일정을 지운다.
     * 두 폰이 같은 id 를 쓰니 동시에 고쳐도 일정 LWW 로 하나만 남는다.
     */
    suspend fun setAnniversary(date: LocalDate?) {
        val id = Event.COUPLE_ANNIVERSARY_ID
        val local = db.eventDao().get(id)?.toModel()
        val at = clock.now()
        val next = if (date == null) {
            local?.takeIf { it.deletedAt == null }?.copy(deletedAt = at) ?: return
        } else {
            (local ?: Event(id, EventType.ANNIVERSARY, "사귄 날", date.atTime(0, 0), date.atTime(23, 59), false, at, 0, identity.deviceId))
                .copy(start = date.atTime(0, 0), end = date.atTime(23, 59), deletedAt = null)
        }
        writer.write(
            OpKind.PROFILE, "event", id,
            localWrite = { lamport -> db.eventDao().upsert(next.copy(lamport = lamport, lastWriterDevice = identity.deviceId, updatedAt = at).toEntity()) },
            payload = { lamport -> OpPayload.EventPut(next.copy(lamport = lamport, lastWriterDevice = identity.deviceId, updatedAt = at)) },
        )
    }
}

class EventRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    /** [from]..[to] 에 걸리는 일정. 매년 반복 기념일은 그 해의 날로 옮겨 놓는다(같은 id — 고치면 원래 일정이 바뀐다). */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Event>> =
        db.eventDao().observeRange("${from}T00:00", "${to}T23:59").map { list ->
            list.flatMap { it.toModel().occurrencesIn(from, to) }.sortedBy { it.start }
        }

    suspend fun get(id: String): Event? = db.eventDao().get(id)?.toModel()

    fun observeNext(): Flow<Event?> =
        db.eventDao().observeNext("${clock.today()}T00:00").map { it?.toModel() }

    suspend fun save(event: Event) {
        writer.write(
            OpKind.EVENT, "event", event.id,
            localWrite = { lamport -> db.eventDao().upsert(stamp(event, lamport).toEntity()) },
            payload = { lamport -> OpPayload.EventPut(stamp(event, lamport)) },
        )
    }

    suspend fun delete(id: String) {
        val local = db.eventDao().get(id) ?: return
        val at = clock.now().toEpochMilliseconds()
        writer.write(
            OpKind.DELETE, "event", id,
            localWrite = { lamport -> db.eventDao().upsert(local.copy(deletedAtMs = at, lamport = lamport, lastWriterDevice = identity.deviceId)) },
            payload = { lamport -> OpPayload.Delete(DeletableEntity.EVENT, id, lamport, at, identity.deviceId) },
        )
    }

    private fun stamp(event: Event, lamport: Long) =
        event.copy(lamport = lamport, lastWriterDevice = identity.deviceId, updatedAt = clock.now())

    /**
     * 그날의 한 줄: 내 줄만 고친다(40자, 빈 글 = 지움). 지운 줄도 시각을 남겨야 상대 폰의 옛 줄이 이기지 않는다.
     * 지운 일정 · 만남이 아닌 일정에는 쓰지 않는다(되살리지 않게).
     */
    suspend fun writeDayLine(eventId: String, text: String) {
        val e = db.eventDao().get(eventId)?.toModel()?.takeIf { it.deletedAt == null && it.hasDayLine } ?: return
        val line = DayLine(text.trim().take(DayLine.MAX_CHARS), clock.now().toEpochMilliseconds())
        save(e.copy(dayLines = e.dayLines + (identity.userId to line)))
    }

    /** [year]년 [month]월에 시작한 만남 중 누군가 그날의 한 줄을 남긴 것(추억 책의 한 달 쪽). */
    fun observeDayLinesIn(year: Int, month: Int): Flow<List<Event>> {
        val first = LocalDate(year, month, 1)
        val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        return db.eventDao().observeDayLines("${first}T00:00", "${last}T23:59:59.999").map { list ->
            list.map { it.toModel() }.filter { e -> e.dayLines.values.any { it.text.isNotBlank() } }
        }
    }

    fun newId(): String = Uuid.random().toString()
}

/**
 * 휴가 하나의 풀(planning §8). 받은 휴가는 조용한 종류(PROFILE)로, 쓴 날은 캘린더 휴가 일정이다 —
 * 그래서 캘린더 · 홈 "다음 휴가"에 그대로 보이고, 일정을 지우면 남은 휴가가 돌아온다. 정기휴가는 군종으로 계산한다.
 */
class LeaveRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    fun observeGrants(): Flow<List<LeaveGrant>> = db.leaveDao().observeAll().map { list -> list.map { it.toModel() } }

    /** 휴가 일정 = 휴가 사용. */
    fun observeUses(): Flow<List<Event>> = db.eventDao().observeVacations().map { list -> list.map { it.toModel() } }

    /** 남은 휴가 / 전체 · 출처 내역. 군인의 복무 정보(혼자 모드면 곰신이 넣은 값)의 군종으로 정기휴가를 센다. */
    fun observePool(): Flow<LeavePool> = combine(db.serviceDao().observeAll(), observeGrants(), observeUses()) { services, grants, uses ->
        LeavePool.of(services.firstOrNull()?.toModel()?.branch, grants, uses)
    }

    suspend fun save(grant: LeaveGrant) {
        writer.write(
            OpKind.PROFILE, "leave", grant.id,
            localWrite = { lamport -> db.leaveDao().upsert(stamp(grant, lamport).toEntity()) },
            payload = { lamport -> OpPayload.LeaveGrantPut(stamp(grant, lamport)) },
        )
    }

    suspend fun delete(id: String) {
        val local = db.leaveDao().get(id) ?: return
        val at = clock.now().toEpochMilliseconds()
        writer.write(
            OpKind.DELETE, "leave", id,
            localWrite = { lamport -> db.leaveDao().upsert(local.copy(deletedAtMs = at, lamport = lamport, lastWriterDevice = identity.deviceId)) },
            payload = { lamport -> OpPayload.Delete(DeletableEntity.LEAVE_GRANT, id, lamport, at, identity.deviceId) },
        )
    }

    /**
     * 사용 등록: [from]..[to] 를 캘린더 휴가 일정으로 남긴다(출처를 고르지 않는다). [notify] 면 상대 폰에 "일정을 바꿨어요" 알림이 간다(EVENT),
     * 아니면 알림 없이 반영한다(PROFILE — 받는 쪽은 종류가 아니라 본문으로 반영한다).
     */
    suspend fun use(from: LocalDate, to: LocalDate, notify: Boolean) {
        val event = Event(
            id = Uuid.random().toString(), type = EventType.VACATION, title = EventType.VACATION.koreanName,
            start = from.atTime(0, 0), end = to.atTime(23, 59), alarm = false,
            updatedAt = clock.now(), lamport = 0, lastWriterDevice = identity.deviceId,
        )
        writer.write(
            if (notify) OpKind.EVENT else OpKind.PROFILE, "event", event.id,
            localWrite = { lamport -> db.eventDao().upsert(event.copy(lamport = lamport).toEntity()) },
            payload = { lamport -> OpPayload.EventPut(event.copy(lamport = lamport)) },
        )
    }

    private fun stamp(grant: LeaveGrant, lamport: Long) =
        grant.copy(lamport = lamport, lastWriterDevice = identity.deviceId, updatedAt = clock.now())

    fun newId(): String = Uuid.random().toString()
}

/** 편지 · 편지 안 그림. 그림만 보내기도 글 없는 편지 한 통이다. */
class LetterRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    /**
     * 편지함. 공개 시각이 지난 것만 — 예약 편지는 공개 전에 어디에도 보이지 않는다.
     * 시각을 쿼리에 고정하면 그 뒤에 보낸 편지 · 공개 시각이 지난 예약 편지가 계속 빠지므로, 시계와 묶어 다시 거른다.
     */
    fun observeInbox(): Flow<List<Letter>> =
        combine(db.letterDao().observeAll(), clock.ticker()) { list, _ ->
            // 틱 값이 아니라 지금 시각: 방금 보낸 편지(공개 시각 = 보낸 시각)가 다음 틱까지 빠지지 않게.
            val now = clock.now().toEpochMilliseconds()
            list.filter { it.visibleAtMs <= now }.map { it.toModel() }
        }.distinctUntilChanged()

    /**
     * 내가 예약해 둔 편지 중 오늘(서울) 아직 도착하지 않은 것(홈 › 오늘 도착할 메시지). 시계와 묶어 도착하면 빠진다.
     */
    fun observeMyScheduledToday(): Flow<List<Letter>> =
        combine(db.letterDao().observeAll(), clock.ticker()) { list, now ->
            val today = now.toLocalDateTime(SEOUL).date
            list.filter {
                it.authorId == identity.userId && it.deletedAtMs == null && it.visibleAtMs > now.toEpochMilliseconds() &&
                    kotlin.time.Instant.fromEpochMilliseconds(it.visibleAtMs).toLocalDateTime(SEOUL).date == today
            }.sortedBy { it.visibleAtMs }.map { it.toModel() }
        }.distinctUntilChanged()

    suspend fun drawingsOf(letterId: String): List<DrawingEntity> = db.drawingDao().forLetter(letterId)

    /** 내가 쓴 편지 수(예약 편지 포함, 지운 것 빼고). 혼자 모드에서 연결하는 순간 한꺼번에 전달되는 양이다. */
    suspend fun countMine(): Int = db.letterDao().byAuthor(identity.userId).count { it.deletedAtMs == null }

    suspend fun send(letter: Letter, drawings: List<DrawingMeta>): String {
        val kind = when {
            letter.visibleAt > clock.now() -> OpKind.SCHEDULED
            !letter.hasText && letter.drawingCount > 0 -> OpKind.LETTER_DRAWING
            else -> OpKind.LETTER
        }
        return writer.write(
            kind, "letter", letter.id,
            localWrite = { lamport ->
                db.letterDao().upsert(letter.toEntity(lamport))
                drawings.forEach { d ->
                    db.drawingDao().upsert(
                        DrawingEntity(
                            d.id, db.coupleDao().get()?.id.orEmpty(), letter.id, d.orderInLetter, identity.userId, d.shape,
                            d.docAttachmentId, d.pngAttachmentId, thumbPath = "", d.caption, d.replayEnabled, d.replayDurationMs,
                            showOnWidget = d.showOnWidget, sentAtMs = clock.now().toEpochMilliseconds(), receivedAtMs = null, deletedAtMs = null,
                        ),
                    )
                }
                db.memoryDao().upsert(MemoryEntity("letter-${letter.id}", "LETTER", letter.id, letter.visibleAt.toString().take(10)))
            },
            payload = { OpPayload.LetterPut(letter, drawings) },
        )
    }

    /** 받은 편지를 처음 읽었다. 이미 기록돼 있으면 아무 일도 하지 않는다(먼저 온 값 유지). */
    suspend fun markRead(entity: ReceiptEntity, entityId: String) {
        if (db.receiptDao().get(entity.name, entityId, identity.userId) != null) return
        val receipt = Receipt(entity, entityId, identity.userId, clock.now())
        writer.write(
            OpKind.RECEIPT, "receipt", "${entity.name}:$entityId",
            localWrite = { db.receiptDao().insertFirst(receipt.toEntity()) },
            payload = { OpPayload.ReceiptPut(receipt) },
        )
    }

    /** 내가 읽은 편지 id. 받은 편지 중 여기 없는 것이 안 읽은 편지다. */
    /** 편지마다 받은 하트 수(받은 편지 › 고마워). */
    fun observeHearts(): Flow<Map<String, Int>> =
        db.receiptDao().observeAllIds(ReceiptEntity.HEART.name).map { ids -> ids.groupingBy { it }.eachCount() }

    fun observeReadLetterIds(): Flow<Set<String>> =
        db.receiptDao().observeIds(ReceiptEntity.LETTER.name, identity.userId).map { it.toSet() }

    suspend fun isFirstView(drawingId: String): Boolean =
        db.receiptDao().get(ReceiptEntity.DRAWING.name, drawingId, identity.userId) == null

    /** 편지를 지우면 안의 그림도 함께 지운다(그림만 따로 지우지 않는다). */
    suspend fun delete(letterId: String) {
        val local = db.letterDao().get(letterId) ?: return
        val at = clock.now().toEpochMilliseconds()
        writer.write(
            OpKind.DELETE, "letter", letterId,
            localWrite = {
                db.letterDao().upsert(local.copy(deletedAtMs = at))
                db.drawingDao().tombstoneForLetter(letterId, at)
                db.memoryDao().deleteForRef(letterId)
            },
            payload = { lamport -> OpPayload.Delete(DeletableEntity.LETTER, letterId, lamport, at, identity.deviceId) },
        )
    }

    fun newLetter(blocks: List<LetterBlock>, paper: String = "plain", title: String? = null, visibleAt: kotlin.time.Instant = clock.now(), font: String? = null) =
        Letter(Uuid.random().toString(), identity.userId, title, blocks, paper, visibleAt, font = font)
}

class MissYouRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    /** 오늘(서울 기준) 상대가 보낸 보고싶어 횟수. */
    fun observeTodayFrom(authorId: String): Flow<Int> {
        val start = clock.today().atStartOfDayIn(SEOUL).toEpochMilliseconds()
        return db.missYouDao().observeCountSince(authorId, start)
    }

    suspend fun send() {
        val m = MissYou(Uuid.random().toString(), identity.userId, clock.now())
        writer.write(
            OpKind.MISS_YOU, "missyou", m.id,
            localWrite = { db.missYouDao().insert(m.toEntity()) },
            payload = { OpPayload.MissYouPut(m) },
        )
    }
}

class StatusRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    fun observeLatest(authorId: String): Flow<Status?> =
        db.statusDao().observeLatest(authorId, clock.now().toEpochMilliseconds()).map { it?.toModel() }

    suspend fun set(label: String, color: Long, expiresAt: kotlin.time.Instant? = null) {
        val s = Status(Uuid.random().toString(), identity.userId, label, color, clock.now(), expiresAt)
        writer.write(
            OpKind.STATUS, "status", s.id,
            localWrite = { lamport -> db.statusDao().upsert(s.copy(lamport = lamport).toEntity()) },
            payload = { lamport -> OpPayload.StatusPut(s.copy(lamport = lamport)) },
        )
    }
}

/**
 * 추억 책 표지(#23 결정 1): 산 사람이 고르고 두 사람이 같은 표지를 쓴다. 구매 뒤에도 언제든 바꾼다(PDF 는 다시 만든다).
 * 커플에 하나인 값이라 조용한 종류(PROFILE)의 `bookcover` Op 으로 보내고, 받는 쪽은 행 LWW 로 합친다.
 */
class BookCoverRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
) {
    fun observe(): Flow<BookCover> = db.bookCoverDao().observe().map { BookCover.from(it?.cover) }

    suspend fun choose(cover: BookCover) {
        writer.write(
            OpKind.PROFILE, "book_cover", "book",
            localWrite = { lamport -> db.bookCoverDao().upsert(BookCoverEntity(cover = cover.name, lamport = lamport, lastWriterDevice = identity.deviceId)) },
            payload = { lamport -> OpPayload.BookCoverPut(cover.name, lamport, identity.deviceId) },
        )
    }
}
