package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DrawingEntity
import com.sadturtleman.flowershoes.core.database.MemoryEntity
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.MissYou
import com.sadturtleman.flowershoes.core.model.Receipt
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.Status
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn

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
) {
    fun observeUsers(): Flow<List<UserEntity>> = db.userDao().observeAll()

    fun myUserId(): String = identity.userId

    /** 커플 중 군인의 복무 일정. 혼자 모드에서 곰신이 입력한 값이어도 여기서 보인다. */
    fun observeTimelines(): Flow<List<Pair<Service, ServiceTimeline>>> =
        db.serviceDao().observeAll().map { list -> list.map { it.toModel() }.map { it to calculator.timeline(it) } }

    suspend fun saveMe(role: Role, nickname: String) {
        val existing = db.userDao().get(identity.userId)
        db.userDao().upsert(UserEntity(identity.userId, role.name, nickname, (existing?.nicknameLamport ?: 0) + 1))
    }

    suspend fun saveService(service: Service) {
        writer.write(
            OpKind.PROFILE, "service", service.userId,
            localWrite = { lamport -> db.serviceDao().upsert(service.copy(lamport = lamport).toEntity()) },
            payload = { lamport -> OpPayload.ServicePut(service.copy(lamport = lamport)) },
        )
    }

    suspend fun setAnniversary(date: LocalDate?) {
        writer.write(
            OpKind.PROFILE, "anniversary", "couple",
            localWrite = { lamport ->
                val couple = db.coupleDao().get() ?: CoupleEntity("solo", identity.userId, "", 0, 0, null, 0)
                db.coupleDao().upsert(couple.copy(anniversary = date?.toString(), anniversaryLamport = lamport))
            },
            payload = { lamport -> OpPayload.AnniversaryPut(date?.toString(), lamport) },
        )
    }
}

class EventRepository(
    private val db: AppDatabase,
    private val writer: SyncWriter,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Event>> =
        db.eventDao().observeRange("${from}T00:00", "${to}T23:59").map { list -> list.map { it.toModel() } }

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
        combine(db.letterDao().observeAll(), clock.ticker()) { list, now ->
            list.filter { it.visibleAtMs <= now.toEpochMilliseconds() }.map { it.toModel() }
        }.distinctUntilChanged()

    suspend fun drawingsOf(letterId: String): List<DrawingEntity> = db.drawingDao().forLetter(letterId)

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
                            showOnWidget = false, sentAtMs = clock.now().toEpochMilliseconds(), receivedAtMs = null, deletedAtMs = null,
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

    fun newLetter(blocks: List<LetterBlock>, paper: String = "plain", title: String? = null, visibleAt: kotlin.time.Instant = clock.now()) =
        Letter(Uuid.random().toString(), identity.userId, title, blocks, paper, visibleAt)
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
