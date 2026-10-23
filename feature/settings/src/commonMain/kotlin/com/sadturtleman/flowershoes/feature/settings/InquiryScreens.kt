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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.data.INQUIRY_TYPES
import com.sadturtleman.flowershoes.core.data.Inquiry
import com.sadturtleman.flowershoes.core.data.InquiryTransport
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkListRow
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkRowTrailing
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
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
    var type by remember { mutableStateOf(INQUIRY_TYPES.first()) }
    var body by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(transport?.accountEmail().orEmpty()) }
    var withDevice by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    val shots = remember { mutableStateListOf<ByteArray>() }
    val pick = photoPicker?.invoke { if (shots.size < MAX_SHOTS) shots.add(it) }
    val emailOk = email.isBlank() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    val canSend = transport != null && body.isNotBlank() && email.isNotBlank() && emailOk && !sending

    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("문의하기", onLeading = onBack, trailingText = "내역", onTrailing = onHistory, trailingEnabled = transport != null)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            if (transport == null) {
                Text("이 빌드에는 서버 설정이 없어 문의를 보낼 수 없어요.", style = KkTheme.type.bodyS, color = c.statusDanger)
            }
            Text("문의 유형", style = KkTheme.type.labelM, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                INQUIRY_TYPES.forEach { t ->
                    KkStatusChip(t, selected = type == t, onClick = { type = t }, dot = if (type == t) Color.White else c.statusWarning)
                }
            }
            Text("내용", style = KkTheme.type.labelM, color = c.textSecondary)
            TextArea(body, { body = it.take(MAX_BODY) }, placeholder = "어떤 일이 있었는지 적어 주세요. 언제 · 어느 화면에서인지 알려 주시면 더 빨리 도와드릴 수 있어요.")
            if (pick != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shots.forEachIndexed { i, bytes ->
                        val bitmap = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
                                .clickable(role = Role.Button) { shots.removeAt(i) }
                                .semantics { contentDescription = "스크린샷 ${i + 1} 빼기" },
                        ) {
                            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(c.bgInverse.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("×", style = KkTheme.type.labelS, color = c.textInverse) }
                        }
                    }
                    if (shots.size < MAX_SHOTS) {
                        Column(
                            Modifier.size(72.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
                                .clickable(role = Role.Button) { pick(false) }.semantics { contentDescription = "스크린샷 추가" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Text("+", style = KkTheme.type.titleM, color = c.textSecondary)
                            Text("${shots.size} / $MAX_SHOTS", style = KkTheme.type.caption, color = c.textTertiary)
                        }
                    }
                }
            }
            KkInput(
                email, { email = it.take(200) }, label = "답변 받을 이메일", placeholder = "minjun@example.com",
                error = if (!emailOk) "이메일 주소를 확인해 주세요" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            val shape = RoundedCornerShape(KkRadius.lg)
            Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)) {
                KkListRow("기기 정보 함께 보내기", value = "기종 · OS · 앱 버전", trailing = KkRowTrailing.Switch, switchOn = withDevice, onClick = { withDevice = !withDevice })
            }
        }
        Column(Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp)) {
            KkPrimaryButton("보내기", {
                val t = transport ?: return@KkPrimaryButton
                sending = true
                scope.launch {
                    val device = if (withDevice) listOf(platform.deviceName, platform.osVersion, "꽃신 ${platform.appVersion}").filter { it.isNotBlank() }.joinToString(" · ") else null
                    runCatching { t.submit(type, body.trim(), email.trim(), device, shots.toList()) }
                        .onSuccess { onSent("문의를 보냈어요. 답변이 오면 알려 드릴게요", true) }
                        .onFailure { onSent("보내지 못했어요. 연결을 확인하고 다시 해 주세요", false) }
                    sending = false
                }
            }, Modifier.fillMaxWidth(), enabled = canSend, loading = sending)
        }
    }
}

/** 여러 줄 입력(150 높이, 반경 10). 누르면 2px 포커스 테두리, 오른쪽 아래 글자 수. */
@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(KkRadius.md)
    Column(
        Modifier.fillMaxWidth().height(150.dp).clip(shape).background(c.bgSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.bgBrand else c.borderDefault, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (value.isEmpty()) Text(placeholder, style = KkTheme.type.bodyM, color = c.textTertiary)
            BasicTextField(
                value, onValueChange,
                textStyle = KkTheme.type.bodyM.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.bgBrand),
                interactionSource = interaction,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "내용" },
            )
        }
        Text("${value.length} / $MAX_BODY", style = KkTheme.type.caption, color = c.textTertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

/** 문의 내역(Figma 7 · 설정 · 문의 내역): 유형 · 날짜 · 답변 상태, 답변이 있으면 아래 회색 칸. */
@Composable
internal fun InquiryHistoryPage(transport: InquiryTransport?, onBack: () -> Unit) {
    val c = KkTheme.colors
    val flow = remember(transport) { transport?.observeMine() ?: emptyFlow() }
    var list by remember { mutableStateOf<List<Inquiry>?>(null) }
    LaunchedEffect(flow) { runCatching { flow.collect { list = it } }.onFailure { if (list == null) list = emptyList() } }
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("문의 내역", onLeading = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            val items = list
            when {
                items == null -> Unit
                items.isEmpty() -> Text("아직 보낸 문의가 없어요.", style = KkTheme.type.bodyS, color = c.textSecondary)
                else -> items.forEach { InquiryCard(it) }
            }
        }
    }
}

@Composable
private fun InquiryCard(q: Inquiry) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${q.type} · ${md(q.createdAtMs)}", style = KkTheme.type.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
            val answered = q.answer != null
            Text(
                if (answered) "답변 완료" else "답변 대기",
                style = KkTheme.type.labelS,
                color = if (answered) c.textBrand else c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(if (answered) c.bgBrandSubtle else c.bgSubtle)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(q.title, style = KkTheme.type.titleS, color = c.textPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        q.answer?.let { a ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("꽃신 답변" + (q.answeredAtMs?.let { " · ${md(it)}" } ?: ""), style = KkTheme.type.caption, color = c.textTertiary)
                Text(a, style = KkTheme.type.bodyS, color = c.textPrimary)
            }
        }
    }
}

private fun md(ms: Long): String {
    if (ms <= 0) return "방금"
    val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(SEOUL).date
    return "${d.month.number}.${d.day}"
}
