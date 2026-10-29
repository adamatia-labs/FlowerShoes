package com.sadturtleman.flowershoes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTag
import com.sadturtleman.flowershoes.core.designsystem.KkPaperTagStyle
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTape
import com.sadturtleman.flowershoes.core.designsystem.KkTheme

// Figma 9 · 공통 상태(385:5342): 앱 시작 · 네트워크 없음 · 서버 오류 · 업데이트 필요 · 점검 중.
// 모두 같은 틀이다 — canvas 바탕, 가운데보다 조금 위에 아이콘 · 손글씨 제목 · 본문, 아래에 동작.

/** 앱 시작(385:5348). 신원 · 앱 막기 판단을 읽는 동안. 시스템 스플래시(Android SplashScreen · iOS 실행 화면) 다음에 이어진다. */
@Composable
internal fun SplashScreen() {
    val c = KkTheme.colors
    val t = KkTheme.type
    StateFrame(footer = {
        Text("adamatia labs", style = t.caption, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 35.dp))
    }) {
        Box(contentAlignment = Alignment.TopCenter) {
            KkIcon(KkIcons.Sprig, tint = c.plant, size = 72.dp)
            KkTape(Modifier.offset(y = (-14).dp).rotate(4f))
        }
        Text("꽃신", style = t.ddayM, color = c.ink)
        Text(SLOGAN, style = t.handLetter, color = c.sub, textAlign = TextAlign.Center)
    }
}

/**
 * 네트워크 없음(385:5360). 막지 않는다 — 뒤로 가기로 닫고 앱을 그대로 쓸 수 있다(써 둔 것은 폰에 남는다).
 * 다시 연결되면 저절로 사라진다. [onRetry] 는 동기화를 한 번 부른다.
 */
@Composable
internal fun OfflineScreen(onRetry: () -> Unit) {
    val c = KkTheme.colors
    StateFrame(actions = { KkPrimaryButton("다시 시도", onRetry) }) {
        KkIcon(KkIcons.Offline, tint = c.sub, size = 56.dp)
        StateTitle("인터넷이 끊겼어요")
        StateBody("써 둔 편지는 폰에 저장돼 있어요. 다시 연결되면 바로 보내요.")
    }
}

/**
 * 서버 오류(385:5372). 동기화가 실패하고 있고 네트워크는 있다. 막지 않는다(뒤로 가기로 닫는다).
 * @param detail "오류 코드 UNAVAILABLE · 10:42" 처럼 한 줄. 코드를 모르면 시각만.
 */
@Composable
internal fun ServerErrorScreen(detail: String, retrying: Boolean, onRetry: () -> Unit, onContact: (() -> Unit)?) {
    val c = KkTheme.colors
    StateFrame(actions = {
        KkPrimaryButton("다시 시도", onRetry, loading = retrying)
        if (onContact != null) TextAction("문의하기", onContact)
    }) {
        KkIcon(KkIcons.Alert, tint = c.danger, size = 56.dp)
        StateTitle("잠깐 문제가 생겼어요")
        StateBody("꽃신 서버가 답하지 않아요. 편지와 사진은 폰에 그대로 있어요.")
        Text(detail, style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center)
    }
}

/** 업데이트 필요(385:5388). 모든 화면을 막는다. 업데이트하기는 스토어를 연다. */
@Composable
internal fun UpdateRequiredScreen(minVersion: String, onUpdate: () -> Unit) {
    StateFrame(actions = { KkPrimaryButton("업데이트하기", onUpdate) }) {
        // 태그는 보여 주기만 하는 물건이라 기울인다(design.md). 테이프는 태그가 붙여 준다.
        KkPaperTag("새 버전", minVersion, valueStyle = KkPaperTagStyle.Hand, tilt = 2f)
        Spacer(Modifier.height(45.dp))
        StateTitle("업데이트가 필요해요")
        StateBody("이 버전으로는 편지를 주고받을 수 없어요. 기록은 그대로 남아요.")
    }
}

/**
 * 점검 중(385:5403). 모든 화면을 막는다. 리모트는 켜고 끄는 값만 주므로(#72 결정) 시간 · 남은 시간 줄과 공지 보기는 없다.
 * [onRetry] 는 판단을 다시 읽는다 — 점검이 끝났으면 바로 열린다.
 */
@Composable
internal fun MaintenanceScreen(onRetry: () -> Unit) {
    val c = KkTheme.colors
    StateFrame(actions = { TextAction("다시 시도", onRetry) }) {
        KkIcon(KkIcons.Gear, tint = c.sub, size = 56.dp)
        StateTitle("잠시 점검 중이에요")
        StateBody("편지와 사진은 폰에 그대로 있어요.")
    }
}

/** 슬로건은 하나(design.md): 로그인 · 스플래시 · 스토어 · OG. */
internal const val SLOGAN = "편지로 이어지는 우리 둘만의 달력"

/**
 * 공통 상태 틀. 아래 화면을 누르지 못하게 바탕이 터치를 먹는다.
 * 본문은 Figma 처럼 가운데보다 조금 위(760 중 250), 동작은 아래 24 여백.
 */
@Composable
private fun StateFrame(
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(KkTheme.colors.canvas)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            Modifier.align(BodyAlignment).widthIn(max = 600.dp).padding(horizontal = KkSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            content = body,
        )
        if (actions != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth()
                    .padding(start = KkSpacing.xxl, end = KkSpacing.xxl, bottom = KkSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
                content = actions,
            )
        }
        if (footer != null) Box(Modifier.align(Alignment.BottomCenter), content = footer)
    }
}

/** 본문 묶음의 세로 자리. Figma 760 높이에서 위 250 · 아래 약 310 이라 가운데보다 조금 위다. */
private val BodyAlignment = BiasAlignment(0f, -0.15f)

@Composable
private fun StateTitle(text: String) = Text(text, style = KkTheme.type.handL, color = KkTheme.colors.ink, textAlign = TextAlign.Center)

@Composable
private fun StateBody(text: String) = Text(text, style = KkTheme.type.body, color = KkTheme.colors.sub, textAlign = TextAlign.Center)

/** Figma Button · Text 스타일(340:118): 바탕 없이 accent 굵은 글자, 높이 50. 같은 화면의 Primary 아래 보조 동작. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KkTheme.type.labelBold, color = KkTheme.colors.accent)
    }
}
