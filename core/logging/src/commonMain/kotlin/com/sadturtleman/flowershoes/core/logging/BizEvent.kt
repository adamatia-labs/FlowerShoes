package com.sadturtleman.flowershoes.core.logging

/**
 * 남길 수 있는 이벤트 전부. 이름과 함께 그 이벤트에 필요한 값도 여기에 담는다.
 *
 * 값을 [BizLogger.record] 의 맵 인자로 받지 않고 이벤트가 들고 있게 한 이유는,
 * 맵으로는 무엇이 필요한지 아무도 강제하지 못하기 때문이다 — 키를 빠뜨려도, 오타를 내도,
 * 엉뚱한 타입을 넣어도 컴파일은 그대로 되고 지표가 빈 뒤에야 드러난다.
 * 생성자로 받으면 빠뜨린 자리에서 컴파일이 멈추고, 어떤 값이 필요한지도 이 파일만 보면 된다.
 *
 * 이름을 부르는 자리에서 짓지 않고 모아 두는 이유도 같다 —
 * 이름은 수집된 뒤 지표의 키가 되므로 오타 하나가 그 이벤트를 통째로 사라지게 만들고,
 * 한 곳에 모여 있어야 같은 행동에 두 이름이 붙는 것을 리뷰에서 바로 본다.
 *
 * sealed 라 이 파일에 없는 이벤트는 아예 남길 수 없다. 새 이벤트는 여기에 한 덩어리를 더한다.
 *
 * [name] 은 수집 쪽과 맞춘 값이라 한번 정하면 바꾸지 않는다 —
 * 바꾸면 그 시점을 기준으로 지표가 둘로 갈린다. 코드에서 부르는 이름만 바꾼다.
 *
 * 값에는 화면 이름 · 선택지(앱이 정한 id) · 숫자 · 참거짓만 싣는다. 사용자가 입력한 글자
 * (편지 본문 · 제목 · 그림 한 줄 · 닉네임 · 코드)는 어떤 모양으로도 싣지 않고, 그래서 마스킹도 두지 않는다
 * (docs/agents/code-review-standards.md). GA4 한도는 이름 40자 · 파라미터 25개(view_name 포함)다.
 */
sealed interface BizEvent {

    /** 수집 쪽에서 이 이벤트를 가리키는 이름. */
    val name: String

    /**
     * 이 이벤트가 함께 나르는 값.
     *
     * 게으르게 계산하지 않고 만들 때 채운다 — 전송은 IO 로 넘어가 나중에 도는데,
     * 그때 값을 읽으면 그 사이 부른 쪽이 바꾼 것이 따라 나간다.
     */
    val parameters: Map<String, Any?>

    /**
     * 화면에 들어왔다.
     *
     * @param from 직전 화면. 앱을 열자마자 들어온 첫 화면이면 null 이다.
     */
    data class ViewEnter(val from: String?) : BizEvent {
        override val name = "view_enter"
        override val parameters: Map<String, Any?> = mapOf("from" to from)
    }

    /**
     * 이 실행에서 사용자가 어떤 피처 플래그 · AB 테스트에 놓였는지.
     *
     * 앱이 뜨면서 플래그가 정해진 자리에서 한 번 남긴다. 사용자 식별자는 기록 한 건마다
     * [BizLogger] 가 붙이므로, 지표에서 배정과 그 뒤의 행동이 같은 키로 이어진다.
     *
     * 플래그 이름을 그대로 키로 쓰지 않고 `flag_` 를 붙이는 것은 environment 같은 이름과
     * 부딪히지 않게 하려는 것이다.
     *
     * @param environment dev · qa · prod 중 이 빌드의 환경. 같은 플래그도 환경마다 값이 다르다.
     * @param assignments 플래그 이름 → 이 사용자가 받은 값.
     */
    data class FlagsResolved(
        val environment: String,
        val assignments: Map<String, String>,
        /** 플래그가 많아 [split] 으로 나뉘었을 때 몇 번째 조각인가(1부터). */
        val part: Int = 1,
    ) : BizEvent {
        override val name = "flags_resolved"
        override val parameters: Map<String, Any?> =
            mapOf("environment" to environment, "part" to part) + assignments.mapKeys { (key, _) -> "flag_$key" }

        companion object {
            /** 한 조각에 싣는 플래그 수. GA4 한도 25 에서 view_name · environment · part 를 뺐다. */
            const val FLAGS_PER_PART = Ga4Limits.MAX_PARAMETERS - 3

            /** 플래그 수만큼 파라미터가 늘어나므로, GA4 한도를 넘지 않게 [FLAGS_PER_PART] 개씩 나눈다. */
            fun split(environment: String, assignments: Map<String, String>): List<FlagsResolved> =
                assignments.entries.chunked(FLAGS_PER_PART)
                    .ifEmpty { listOf(emptyList()) }
                    .mapIndexed { i, chunk -> FlagsResolved(environment, chunk.associate { it.key to it.value }, part = i + 1) }
        }
    }

    /**
     * AB 테스트의 변형이 실제로 화면에 쓰였다.
     *
     * [FlagsResolved] 와 따로 두는 이유는 배정과 노출이 다르기 때문이다 — 배정만 받고
     * 그 화면에 한 번도 들어오지 않은 사용자를 실험 결과에 넣으면 차이가 묽어진다.
     */
    data class AbExposed(val experiment: String, val variant: String) : BizEvent {
        override val name = "ab_exposed"
        override val parameters: Map<String, Any?> =
            mapOf("experiment" to experiment, "variant" to variant)
    }

    /**
     * 목록의 보기 방식(리스트 · 그리드)을 바꿨다.
     *
     * @param layout 바꾼 뒤의 보기. 화면이 쓰는 타입을 도메인이 알 수 없으므로 이름을 넘긴다.
     */
    data class LayoutToggle(val layout: String) : BizEvent {
        override val name = "layout_toggle"
        override val parameters: Map<String, Any?> = mapOf("layout" to layout)
    }

    /** 연결 초대(링크 · 코드)를 만들었다. */
    data object PairingLinkCreated : BizEvent {
        override val name = "pairing_link_created"
        override val parameters: Map<String, Any?> = emptyMap()
    }

    /** 받은 초대를 열어 연결을 수락했다. */
    data object PairingLinkAccepted : BizEvent {
        override val name = "pairing_link_accepted"
        override val parameters: Map<String, Any?> = emptyMap()
    }

    /**
     * 두 폰이 연결됐다. 퍼널의 끝이다(연결 완료 커플 수의 기준은 서버 집계이고, 이 이벤트는 기기 쪽 흐름을 본다).
     *
     * @param relink 기기 교체로 다시 연결했다(처음 연결이 아니다).
     */
    data class PairingLinked(val relink: Boolean) : BizEvent {
        override val name = "pairing_linked"
        override val parameters: Map<String, Any?> = mapOf("relink" to relink)
    }

    /**
     * 편지를 보냈다. 본문 · 제목은 싣지 않는다 — 고른 것과 개수만.
     *
     * @param paper 편지지 id. @param font 글꼴 id. 둘 다 앱이 정한 선택지다.
     * @param scheduled 예약 공개다. @param reply 받은 편지에서 누른 "고마워" 답장이다.
     */
    data class LetterSent(
        val paper: String,
        val font: String,
        val scheduled: Boolean,
        val drawings: Int,
        val photos: Int,
        val reply: Boolean,
    ) : BizEvent {
        override val name = "letter_sent"
        override val parameters: Map<String, Any?> =
            mapOf("paper" to paper, "font" to font, "scheduled" to scheduled, "drawings" to drawings, "photos" to photos, "reply" to reply)
    }

    /**
     * 그림만 보내기로 그림 한 장을 보냈다.
     *
     * @param withCaption 한 줄을 붙였다. 그 글자는 싣지 않는다.
     */
    data class DrawingSent(val scheduled: Boolean, val withCaption: Boolean) : BizEvent {
        override val name = "drawing_sent"
        override val parameters: Map<String, Any?> = mapOf("scheduled" to scheduled, "caption" to withCaption)
    }

    /**
     * 혼자 모드(상대 연결 전)에서 편지를 보내 내 폰에 쌓였다.
     *
     * @param count 지금까지 쌓인 내 편지 수. 연결하는 순간 한꺼번에 전달되는 양이다("편지 N통 도착").
     */
    data class SoloLettersQueued(val count: Int) : BizEvent {
        override val name = "solo_letters_queued"
        override val parameters: Map<String, Any?> = mapOf("count" to count)
    }

    /** 결제 창을 띄웠다. */
    data class PurchaseStarted(val product: PurchaseProduct) : BizEvent {
        override val name = "purchase_started"
        override val parameters: Map<String, Any?> = mapOf("product" to product.id)
    }

    /** 결제가 끝나 이용권을 받았다. 취소 · 실패 · 구매 복원은 남기지 않는다. */
    data class PurchaseCompleted(val product: PurchaseProduct) : BizEvent {
        override val name = "purchase_completed"
        override val parameters: Map<String, Any?> = mapOf("product" to product.id)
    }
}

/** 파는 상품. [id] 는 수집 쪽 값이라 바꾸지 않는다. */
enum class PurchaseProduct(val id: String) {
    LIFETIME("lifetime"),
    MEMORY_BOOK("memory_book"),
}

/**
 * 이벤트를 남기는 화면 이름. 이벤트 이름처럼 수집 뒤 지표의 키가 되므로 한 곳에 모은다.
 * 라우팅 경로 모양을 따른다.
 */
object BizView {
    const val PAIRING = "/pairing"
    const val LETTER_COMPOSE = "/letter/compose"
    const val LETTER_READ = "/letter/read"
    const val DRAWING_SEND = "/drawing/send"
    const val LIFETIME = "/settings/lifetime"
    const val MEMORY_BOOK = "/memory/book"
}
