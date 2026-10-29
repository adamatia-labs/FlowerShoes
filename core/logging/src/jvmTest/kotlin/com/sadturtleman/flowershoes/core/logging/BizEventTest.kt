package com.sadturtleman.flowershoes.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이벤트 목록 자체의 규칙.
 *
 * GA4 한도(이름 40자 · 파라미터 25개)는 수집 쪽에서 조용히 버려지므로, 이벤트를 더하는 자리에서 막는다.
 * [every] 가 `when` 으로 모든 이벤트를 짚으므로 새 이벤트를 더하면 여기서 컴파일이 멈춘다 — 견본을 하나 더한다.
 */
class BizEventTest {

    @Test
    fun `모든 이벤트 이름은 GA4 한도 안이고 소문자 · 숫자 · 밑줄뿐이다`() {
        samples.forEach { event ->
            assertTrue(event.name.length <= 40, "${event.name} 이 40자를 넘는다")
            assertTrue(Regex("[a-z][a-z0-9_]*").matches(event.name), "${event.name} 에 쓸 수 없는 글자가 있다")
        }
    }

    /** 화면 이름(view_name)이 한 자리를 더 쓴다. */
    @Test
    fun `모든 이벤트의 파라미터는 화면 이름을 더해도 25개 안이다`() {
        samples.forEach { event -> assertTrue(event.parameters.size + 1 <= 25, "${event.name} 파라미터가 너무 많다") }
    }

    @Test
    fun `퍼널 이벤트는 선택지와 숫자만 나른다`() {
        assertEquals(
            mapOf("paper" to "blossom", "font" to "sans", "scheduled" to true, "drawings" to 1, "photos" to 0, "reply" to false),
            BizEvent.LetterSent(paper = "blossom", font = "sans", scheduled = true, drawings = 1, photos = 0, reply = false).parameters,
        )
        assertEquals(mapOf("scheduled" to false, "caption" to true), BizEvent.DrawingSent(scheduled = false, withCaption = true).parameters)
        assertEquals(mapOf("count" to 3), BizEvent.SoloLettersQueued(count = 3).parameters)
        assertEquals(mapOf("relink" to false), BizEvent.PairingLinked(relink = false).parameters)
        assertEquals(mapOf("product" to "memory_book"), BizEvent.PurchaseStarted(PurchaseProduct.MEMORY_BOOK).parameters)
        assertEquals(mapOf("product" to "lifetime"), BizEvent.PurchaseCompleted(PurchaseProduct.LIFETIME).parameters)
    }

    @Test
    fun `플래그가 많으면 한 이벤트가 GA4 한도 안으로 나뉘고 모든 플래그가 한 번씩 실린다`() {
        val assignments = (1..50).associate { "flag$it" to "v$it" }

        val parts = BizEvent.FlagsResolved.split(environment = "prod", assignments = assignments)

        assertEquals(3, parts.size)
        parts.forEach { assertTrue(it.parameters.size + 1 <= 25) }
        assertEquals(listOf(1, 2, 3), parts.map { it.parameters["part"] })
        assertTrue(parts.all { it.parameters["environment"] == "prod" })
        assertEquals(assignments, parts.flatMap { it.assignments.entries }.associate { it.key to it.value })
    }

    @Test
    fun `플래그가 적으면 한 이벤트로 나간다`() {
        val parts = BizEvent.FlagsResolved.split(environment = "dev", assignments = mapOf("new_onboarding" to "true"))

        assertEquals(
            listOf(mapOf("environment" to "dev", "part" to 1, "flag_new_onboarding" to "true")),
            parts.map { it.parameters },
        )
    }

    private val samples: List<BizEvent> = listOf(
        BizEvent.ViewEnter(from = null),
        BizEvent.FlagsResolved(environment = "prod", assignments = (1..22).associate { "f$it" to "on" }),
        BizEvent.AbExposed(experiment = "exp", variant = "B"),
        BizEvent.LayoutToggle(layout = "grid"),
        BizEvent.PairingLinkCreated,
        BizEvent.PairingLinkAccepted,
        BizEvent.PairingLinked(relink = true),
        BizEvent.LetterSent(paper = "plain", font = "sans", scheduled = false, drawings = 0, photos = 0, reply = false),
        BizEvent.DrawingSent(scheduled = false, withCaption = false),
        BizEvent.SoloLettersQueued(count = 1),
        BizEvent.PurchaseStarted(PurchaseProduct.LIFETIME),
        BizEvent.PurchaseCompleted(PurchaseProduct.MEMORY_BOOK),
    ).onEach(::every)

    /** 새 이벤트를 더하면 여기서 컴파일이 멈춘다. [samples] 에 견본을 더하고 가지를 하나 더한다. */
    private fun every(event: BizEvent): Unit = when (event) {
        is BizEvent.ViewEnter, is BizEvent.FlagsResolved, is BizEvent.AbExposed, is BizEvent.LayoutToggle,
        BizEvent.PairingLinkCreated, BizEvent.PairingLinkAccepted, is BizEvent.PairingLinked,
        is BizEvent.LetterSent, is BizEvent.DrawingSent, is BizEvent.SoloLettersQueued,
        is BizEvent.PurchaseStarted, is BizEvent.PurchaseCompleted,
        -> Unit
    }
}
