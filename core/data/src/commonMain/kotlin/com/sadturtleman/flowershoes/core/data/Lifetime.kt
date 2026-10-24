package com.sadturtleman.flowershoes.core.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 결제 결과(설정 › 꽃신 평생). */
sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    /** 구매 복원했는데 산 기록이 없다. */
    data object NothingToRestore : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * 꽃신 평생(한 번 결제). 스토어 결제는 플랫폼(Android: RevenueCat)이 맡는다 — 없는 빌드에서는 등록되지 않는다.
 * [owned] 면 광고를 모두 숨기고 꽃신 평생 혜택을 연다.
 */
interface LifetimeStore {
    val owned: StateFlow<Boolean>

    /** 스토어 표시 가격("₩7,900"). 상품을 못 읽으면 null. */
    suspend fun price(): String?

    suspend fun buy(): PurchaseOutcome

    suspend fun restore(): PurchaseOutcome

    /** 앱이 앞으로 나올 때 · 로그인 뒤: 계정을 맞추고 이용권을 다시 읽는다. */
    suspend fun refresh()
}

/**
 * 추억 책 PDF(한 번 결제, 수익 모델 확정 5,900원). 앱 안의 책은 누구나 보고, 디자인된 PDF(화면용 + 인쇄용)만 판다.
 * 연결된 두 사람 중 한 명이 사면 둘 다 받는다 — 산 사람이 만든 PDF 를 상대 폰도 같은 방법으로 만든다(공유 상태는 [owned] 를 상대 정보에 싣는다).
 * 스토어 결제가 없는 빌드에서는 등록되지 않는다.
 */
interface BookStore : OneTimeProduct

/** 백업 용량 추가(한 번 결제 3,300원 · +5GB, 수익 모델 확정). 백업은 계정마다라 상대와 나누지 않는다. */
interface BackupPlusStore : OneTimeProduct

/** 한 번 사는 상품 하나(스토어 결제). */
interface OneTimeProduct {
    val owned: StateFlow<Boolean>

    suspend fun price(): String?

    suspend fun buy(): PurchaseOutcome

    suspend fun restore(): PurchaseOutcome
}

/**
 * 연결된 두 사람이 함께 쓰는 혜택(수익 모델 확정: "연결된 상대도 광고 제거 · 꾸미기 함께", 추억 책은 "연인도 함께 받아요").
 * 내가 산 것 + 연결된 상대가 산 것(상대 정보의 perks). 내가 산 것은 [publish] 로 내 정보에 실어 상대 폰에 보낸다.
 */
class SharedPerks(private val lifetime: LifetimeStore?, private val book: BookStore?, private val profile: ProfileRepository) {
    private val mine: kotlinx.coroutines.flow.Flow<Set<String>> = kotlinx.coroutines.flow.combine(
        lifetime?.owned ?: kotlinx.coroutines.flow.flowOf(false),
        book?.owned ?: kotlinx.coroutines.flow.flowOf(false),
    ) { l, b -> setOfNotNull(LIFETIME.takeIf { l }, BOOK.takeIf { b }) }

    private val all = kotlinx.coroutines.flow.combine(mine, profile.observePartnerPerks()) { m, p -> m + p }

    /** 광고를 숨기고 꾸미기(글꼴 · 편지지 · 펜 · 색 · 위젯 꾸미기)를 연다. */
    val lifetimeOn: kotlinx.coroutines.flow.Flow<Boolean> = all.map { LIFETIME in it }

    /** 추억 책 PDF 를 받을 수 있다. */
    val bookOn: kotlinx.coroutines.flow.Flow<Boolean> = all.map { BOOK in it }

    /** 내가 산 것이 바뀔 때마다 내 정보에 싣는다(앱이 떠 있는 동안). */
    suspend fun publish() {
        mine.distinctUntilChanged().collect { runCatching { profile.publishPerks(it) } }
    }

    companion object {
        const val LIFETIME = "lifetime"
        const val BOOK = "book"
    }
}

/** 개발 빌드 전용 스위치. [freeBookPdf] 면 결제 없이 추억 책 PDF 를 만들어 볼 수 있다(디버그 빌드만 등록). */
class DevFlags(val freeBookPdf: Boolean)
