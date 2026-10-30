package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.data.INQUIRY_TYPES
import com.sadturtleman.flowershoes.core.data.Inquiry
import com.sadturtleman.flowershoes.core.data.InquiryTransport
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkListRow
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSheetOption
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val MAX_BODY = 1000
private const val MAX_SHOTS = 3

/**
 * 문의하기(Figma 7 · 설정 · 문의하기). 유형 · 내용 · 스크린샷 3장 · 답변 받을 이메일 · 기기 정보.
 * [photoPicker] 는 앱 모듈의 사진 고르기(없으면 스크린샷 칸을 숨긴다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InquiryPage(
    transport: InquiryTransport?,
    platform: PlatformActions,
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))?,
    onHistory: () -> Unit,
    onBack: () -> Unit,
    onSent: (message: String, sent: Boolean) -> Unit,
) {
    val c = KkTheme.colors
    val scope = rememberCoroutineScope()
    // Figma 8 · 문의하기 · 작성: 종류 › (기본 오류 신고) · 내용 · 스크린샷 · 기기 정보 한 줄.
    var type by remember { mutableStateOf(INQUIRY_TYPES.first()) }
    var typeSheet by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(transport?.accountEmail().orEmpty()) }
    var sending by remember { mutableStateOf(false) }
    val device = listOf("앱 버전 ${platform.appVersion}", platform.deviceName, platform.osVersion).filter { it.isNotBlank() }.joinToString(" · ")
    val shots = remember { mutableStateListOf<ByteArray>() }
    val pick = photoPicker?.invoke { if (shots.size < MAX_SHOTS) shots.add(it) }
    var emailSheet by remember { mutableStateOf(false) }
    val canSend = transport != null && body.isNotBlank() && emailValid(email) && !sending

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(
            "문의하기", onLeading = onBack, leading = KkTopBarLeading.Close,
            trailingText = "내역", onTrailing = onHistory, trailingEnabled = transport != null,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            if (transport == null) {
                Text("이 빌드에는 서버 설정이 없어 문의를 보낼 수 없어요.", style = KkTheme.type.body, color = c.danger)
            }
            val shape = RoundedCornerShape(KkRadius.lg)
            // 답변 받을 이메일은 Figma 작성 화면에 칸이 없어 종류 아래 줄로 둔다(계정 이메일이 있으면 미리 채운다).
            Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)) {
                KkListRow("종류", value = type, onClick = { typeSheet = true })
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                KkListRow("답변 받을 이메일", value = email.ifBlank { "정하기" }, onClick = { emailSheet = true })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("내용", style = KkTheme.type.caption, color = c.sub)
                TextArea(body, { body = it.take(MAX_BODY) })
                Text("편지 내용 같은 개인적인 글은 적지 말아 주세요 · ${body.length}/$MAX_BODY", style = KkTheme.type.caption, color = c.sub)
            }
            if (pick != null) {
                Text("스크린샷 (${shots.size}/$MAX_SHOTS)", style = KkTheme.type.labelBold, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shots.forEachIndexed { i, bytes ->
                        val bitmap = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.line)
                                .clickable(role = Role.Button) { shots.removeAt(i) }
                                .semantics { contentDescription = "스크린샷 ${i + 1} 빼기" },
                        ) {
                            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("×", style = KkTheme.type.label, color = c.canvas) }
                        }
                    }
                    if (shots.size < MAX_SHOTS) {
                        Column(
                            Modifier.size(72.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.line)
                                .clickable(role = Role.Button) { pick(false) }.semantics { contentDescription = "스크린샷 추가" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Text("+", style = KkTheme.type.title, color = c.sub)
                            Text("추가", style = KkTheme.type.caption, color = c.sub)
                        }
                    }
                }
            }
            // Figma: 기기 정보는 고르지 않고 함께 간다(무엇이 가는지만 한 줄).
            Text("${device.ifBlank { "기기" }} 정보가 함께 가요.", style = KkTheme.type.caption, color = c.sub)
        }
        Column(Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp)) {
            KkPrimaryButton("보내기", {
                val t = transport ?: return@KkPrimaryButton
                sending = true
                scope.launch {
                    runCatching { t.submit(type, body.trim(), email.trim(), device.ifBlank { null }, shots.toList()) }
                        .onSuccess { onSent("문의를 보냈어요. 보통 하루 안에 답해요", true) }
                        .onFailure { onSent("보내지 못했어요. 연결을 확인하고 다시 해 주세요", false) }
                    sending = false
                }
            }, Modifier.fillMaxWidth(), enabled = canSend, loading = sending)
        }
    }
    if (typeSheet) {
        KkModalSheet({ typeSheet = false }) {
            KkSheetHeader("종류")
            INQUIRY_TYPES.forEach { t -> KkSheetOption(t, t == type, { type = t; typeSheet = false }) }
        }
    }
    if (emailSheet) {
        var draft by remember { mutableStateOf(email) }
        val ok = draft.isBlank() || emailValid(draft)
        KkModalSheet({ emailSheet = false }) {
            KkSheetHeader("답변 받을 이메일")
            KkInput(
                draft, { draft = it.take(200) }, label = "이메일",
                error = if (!ok) "이메일 주소를 확인해 주세요" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            KkPrimaryButton("저장", { email = draft.trim(); emailSheet = false }, Modifier.fillMaxWidth(), enabled = emailValid(draft))
        }
    }
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private fun emailValid(email: String) = EMAIL.matches(email.trim())

/** 여러 줄 입력(150 높이, 반경 10). 누르면 2px 포커스 테두리. 글자 수는 아래 도움말 줄에. */
@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(KkRadius.md)
    Column(
        Modifier.fillMaxWidth().height(150.dp).clip(shape).background(c.surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value, onValueChange,
            textStyle = KkTheme.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "내용" },
        )
    }
}

/**
 * 문의 내역(Figma 8 · 문의 내역 `380:5236`): 제목 · 날짜 · 종류 + 답변 칩(대기 · 완료), 누르면 답변. 아래 "새로 문의하기".
 * 비어 있으면 봉투 + "아직 문의한 적이 없어요" + "문의하기"(설명 문장은 design.md 글 규칙대로 뺀다).
 */
@Composable
internal fun InquiryHistoryPage(transport: InquiryTransport?, onBack: () -> Unit, onNew: () -> Unit) {
    val c = KkTheme.colors
    val flow = remember(transport) { transport?.observeMine() ?: emptyFlow() }
    var list by remember { mutableStateOf<List<Inquiry>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flow) { runCatching { flow.collect { list = it } }.onFailure { if (list == null) list = emptyList() } }
    val items = list
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("문의 내역", onLeading = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
        ) {
            when {
                items == null -> Unit
                items.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(top = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
                ) {
                    KkIcon(KkIcons.Mail, tint = c.sub, size = 48.dp)
                    Text("아직 문의한 적이 없어요", style = KkTheme.type.handL, color = c.ink)
                }
                else -> items.forEach { q ->
                    InquiryRow(q, expanded = open == q.id) { open = if (open == q.id) null else q.id }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                }
            }
        }
        if (items != null) {
            Column(Modifier.padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg)) {
                if (items.isEmpty()) KkPrimaryButton("문의하기", onNew, Modifier.fillMaxWidth(), enabled = transport != null)
                else KkButton("새로 문의하기", onNew, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary)
            }
        }
    }
}

/** 문의 한 줄: 제목(Body) + "10.28 · 오류 신고"(Caption) + 답변 칩. 답변이 있으면 눌러서 펼친다. */
@Composable
private fun InquiryRow(q: Inquiry, expanded: Boolean, onToggle: () -> Unit) {
    val c = KkTheme.colors
    val answered = q.answer != null
    Column(
        Modifier.fillMaxWidth()
            .then(if (answered) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(q.title, style = KkTheme.type.body, color = c.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${md(q.createdAtMs)} · ${q.type}", style = KkTheme.type.caption, color = c.sub)
            }
            val chip = RoundedCornerShape(KkRadius.full)
            Text(
                if (answered) "답변 완료" else "답변 대기",
                style = KkTheme.type.label,
                color = if (answered) c.success else c.ink,
                modifier = Modifier.clip(chip)
                    .then(if (answered) Modifier.border(1.dp, c.success, chip) else Modifier.background(c.tag))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        val answer = q.answer
        if (expanded && answer != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("꽃신 답변" + (q.answeredAtMs?.let { " · ${md(it)}" } ?: ""), style = KkTheme.type.caption, color = c.sub)
                Text(answer, style = KkTheme.type.body, color = c.ink)
            }
        }
    }
}

/** "10.28" · "09.21"(Figma 문의 내역). 서버 시각이 아직 없으면 "방금". */
private fun md(ms: Long): String {
    if (ms <= 0) return "방금"
    val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(SEOUL).date
    return "${d.month.number.toString().padStart(2, '0')}.${d.day.toString().padStart(2, '0')}"
}
