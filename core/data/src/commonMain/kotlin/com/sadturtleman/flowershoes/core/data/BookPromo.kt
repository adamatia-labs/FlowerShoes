package com.sadturtleman.flowershoes.core.data

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** 추억 책 권유 팝업을 띄우는 날(planning §2 · §8): 전역 D-30 · 전역일. */
enum class BookPromoDay { D30, DISCHARGE }

/** 이 폰에서 이미 본 팝업 · "다시 보지 않기"(각자 폰). */
data class BookPromoSeen(val d30: Boolean = false, val discharge: Boolean = false, val never: Boolean = false)

/** 이 기기에만 저장하는 권유 팝업 기록. */
interface BookPromoStore {
    val seen: Flow<BookPromoSeen>
    suspend fun markSeen(day: BookPromoDay)
    suspend fun never()
}

/** D-30 팝업은 그날을 놓쳐도 전역 전날까지 한 번 띄운다(그 달에 앱을 열면 본다). */
private const val D30_DAYS = 30

/**
 * 오늘 띄울 권유 팝업(#23 결정 3 · 4). 전역 D-30(놓쳤으면 전역 전날까지) · 전역일에 각 한 번, 각자 폰.
 * 이미 산 커플(둘 중 한 명이 샀으면 둘 다)과 "다시 보지 않기" 뒤, 전역 다음 날부터는 띄우지 않는다.
 */
fun bookPromoDay(today: LocalDate, discharge: LocalDate?, owned: Boolean, seen: BookPromoSeen): BookPromoDay? {
    if (discharge == null || owned || seen.never) return null
    val left = today.daysUntil(discharge)
    return when {
        left == 0 && !seen.discharge -> BookPromoDay.DISCHARGE
        left in 1..D30_DAYS && !seen.d30 -> BookPromoDay.D30
        else -> null
    }
}

/** 홈 위 가운데 팝업의 상태. 전역일은 복무 계산값(ServiceCalculator), 산 것은 [SharedPerks.bookOn]. */
class BookPromo(
    profile: ProfileRepository,
    bookOn: Flow<Boolean>,
    private val store: BookPromoStore,
    clock: Clock = Clock.System,
) {
    val day: Flow<BookPromoDay?> = combine(
        clock.ticker().map { clock.today() }.distinctUntilChanged(),
        profile.observeTimelines().map { it.firstOrNull()?.second?.dischargeDate },
        bookOn,
        store.seen,
    ) { today, discharge, owned, seen -> bookPromoDay(today, discharge, owned, seen) }.distinctUntilChanged()

    /** 닫기 · 추억 책 보기: 이 날의 팝업은 다시 띄우지 않는다. */
    suspend fun close(day: BookPromoDay) = store.markSeen(day)

    /** 다시 보지 않기: D-30 · 전역일 모두. */
    suspend fun never() = store.never()
}
