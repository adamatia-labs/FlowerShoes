package com.sadturtleman.flowershoes.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sadturtleman.flowershoes.MainActivity
import com.sadturtleman.flowershoes.R
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.kkColors
import com.sadturtleman.flowershoes.core.media.DrawingBitmap
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.widget.SnapshotStore
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshot
import com.sadturtleman.flowershoes.core.widget.WidgetTag
import com.sadturtleman.flowershoes.core.widget.justSent
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext

/**
 * 홈 화면 위젯 v2(Figma 9 · 위젯 383:5321). 위젯은 DB 를 읽지 않고 스냅샷 파일만 읽고, 글자도 스냅샷에 만들어져 있다.
 * 색은 기기 테마(스냅샷)의 테마 v2 값([WidgetPalette] 한 곳)이고 라이트/다크는 시스템을 따른다.
 * Glance 로는 기울기 · 손글씨 글꼴을 쓸 수 없어 종이 태그는 반듯하게, 손글씨는 시스템 cursive 로 그린다.
 * Android 에는 잠금 화면 위젯이 없다(iOS accessory 위젯만).
 */
internal fun snapshotStore(context: Context) =
    SnapshotStore(context.filesDir.resolve("widget/snapshot.json").absolutePath.toPath())

/**
 * 위젯 색은 여기서만 정한다. Compose 토큰([KkColors])을 쓸 수 없는 Glance 를 위해 테마 v2 값을 라이트/다크 쌍으로 옮긴다.
 * 그림 프레임(설정 › 위젯, 꽃신 평생)은 편지지처럼 콘텐츠라 테마 · 다크와 무관하게 고정이다.
 */
private class WidgetPalette(s: WidgetSnapshot?) {
    private val theme = AppTheme.from(s?.theme, role = null)
    private val light: KkColors = kkColors(theme, dark = false)
    private val dark: KkColors = kkColors(theme, dark = true)
    private fun of(pick: (KkColors) -> Color) = ColorProvider(day = pick(light), night = pick(dark))
    private fun fixed(c: Long) = ColorProvider(Color(c), Color(c))

    val surface = of { it.surface }
    val tag = of { it.tag }
    val tape = of { it.tape }
    val ink = of { it.ink }
    val sub = of { it.sub }
    val line = of { it.line }
    val accent = of { it.accent }
    val love = of { it.love }
    val loveOn = of { it.loveOn }
    val soldier = theme == AppTheme.SOLDIER

    /** 일정 색(다음 만남 점). */
    fun event(type: String?) = when (type) {
        "VACATION" -> of { it.eventVacation }
        "OVERNIGHT" -> of { it.eventOvernight }
        "VISIT" -> of { it.eventVisit }
        "TRAINING" -> of { it.eventTraining }
        "DUTY" -> of { it.eventDuty }
        "ANNIVERSARY" -> of { it.eventAnniversary }
        else -> sub
    }

    /** 상대가 고른 상태 색(콘텐츠). 없으면 보조 글자 색. */
    fun status(argb: Long?) = argb?.takeIf { it != 0L }?.let(::fixed) ?: sub

    /** 그림 프레임 바탕 + (옆, 아래) 여백. 기본은 null — 테마 표면을 쓴다. */
    fun frame(frame: String?) = when (frame) {
        "BLOSSOM" -> fixed(0xFFFFE0E7) to (14.dp to 14.dp)
        "KRAFT" -> fixed(0xFFE9DCC4) to (14.dp to 14.dp)
        "POLAROID" -> fixed(0xFFFFFFFF) to (10.dp to 30.dp)
        "NIGHT" -> fixed(0xFF1B2235) to (14.dp to 14.dp)
        else -> null
    }
}

/** 테마 v2 텍스트 스타일(Glance 판): Caption 11 · Label 13 · D-day/S 24 · D-day/M 52 · Hand/Letter 21 · Hand/L 26. */
private object WidgetType {
    fun caption(color: ColorProvider) = TextStyle(color = color, fontSize = 11.sp)
    fun label(color: ColorProvider, bold: Boolean = true) =
        TextStyle(color = color, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    /** 숫자: 곰신은 명조(Gowun Batang), 군인은 굵은 고딕(Black Han Sans)에 가깝게. */
    fun number(p: WidgetPalette, size: TextUnit = 24.sp) =
        TextStyle(color = p.ink, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = if (p.soldier) null else FontFamily.Serif)
    fun hand(color: ColorProvider, size: TextUnit = 21.sp) = TextStyle(color = color, fontSize = size, fontFamily = FontFamily.Cursive)
}

private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 250.dp)

private fun Context.openApp(): Action = actionStartActivity<MainActivity>()

private fun Context.openUri(uri: String): Action =
    androidx.glance.appwidget.action.actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri), this, MainActivity::class.java))

/** 모든 위젯이 같은 틀: 둥근 종이 표면 + 누르면 [onClick]. */
@Composable
private fun Card(p: WidgetPalette, onClick: Action, padding: Int = 16, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(p.surface).padding(padding.dp).clickable(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

@Composable
private fun Caption(text: String, color: ColorProvider) = Text(text, style = WidgetType.caption(color), maxLines = 1)

/** 종이 태그(Paper Tag): tag 바탕 위 ink 글자만. */
@Composable
private fun PaperTag(p: WidgetPalette, tag: WidgetTag, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier.background(p.tag).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(tag.label, style = WidgetType.caption(p.ink), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        Text(tag.value, style = WidgetType.label(p.ink), maxLines = 1)
    }
}

@Composable
private fun PaperTags(p: WidgetPalette, tags: List<WidgetTag>) {
    Row {
        tags.forEachIndexed { i, t ->
            if (i > 0) Spacer(GlanceModifier.width(8.dp))
            PaperTag(p, t)
        }
    }
}

/** 복무 진행(Service Progress): 지난 길(accent) · 남은 길(line) + 입대 · 진행률 · 전역. */
@Composable
private fun ServiceProgress(p: WidgetPalette, s: WidgetSnapshot) {
    val progress = s.progress ?: return
    Column(GlanceModifier.fillMaxWidth()) {
        LinearProgressIndicator(progress, GlanceModifier.fillMaxWidth().height(3.dp), color = p.accent, backgroundColor = p.line)
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Caption(s.progressStart.orEmpty(), p.sub)
            Spacer(GlanceModifier.defaultWeight())
            Caption(s.progressPercent.orEmpty(), p.accent)
            Spacer(GlanceModifier.defaultWeight())
            Caption(s.progressEnd.orEmpty(), p.sub)
        }
    }
}

/** 보고싶어 버튼(Miss You FAB): love 바탕 + loveOn 하트 · 글자. 보낸 직후엔 체크 + "보냈어요". */
@Composable
private fun MissYouFab(p: WidgetPalette, sent: Boolean) {
    Row(
        GlanceModifier.cornerRadius(999.dp).background(p.love).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(if (sent) R.drawable.widget_ic_check else R.drawable.ic_heart_white), contentDescription = null,
            modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(p.loveOn),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(if (sent) "보냈어요" else "보고싶어", style = WidgetType.label(p.loveOn), maxLines = 1)
    }
}

/** 작은 줄 앞 8dp 동그라미(일정 · 상태 색). */
@Composable
private fun Dot(color: ColorProvider) {
    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)) {}
}

// ── 전역 D-day(작게 2×2 · 중간 4×2) ──────────────────────────────────────

class DdayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        provideContent {
            GlanceTheme {
                Card(p, context.openApp()) {
                    if (s?.ddayValue == null) {
                        Caption("전역까지", p.sub)
                    } else if (LocalSize.current.width >= MEDIUM.width) {
                        DdayMedium(p, s)
                    } else {
                        DdaySmall(p, s)
                    }
                }
            }
        }
    }
}

@Composable
private fun DdaySmall(p: WidgetPalette, s: WidgetSnapshot) {
    Column(GlanceModifier.fillMaxSize()) {
        Caption(s.ddayCaption.orEmpty(), p.sub)
        Spacer(GlanceModifier.height(8.dp))
        Text(s.ddayValue.orEmpty(), style = WidgetType.number(p), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        s.progress?.let {
            LinearProgressIndicator(it, GlanceModifier.fillMaxWidth().height(4.dp), color = p.accent, backgroundColor = p.line)
            Spacer(GlanceModifier.height(8.dp))
        }
        s.ddayFoot?.let { Caption(it, p.sub) }
    }
}

@Composable
private fun DdayMedium(p: WidgetPalette, s: WidgetSnapshot) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                Caption(s.ddayCaption.orEmpty(), p.sub)
                Spacer(GlanceModifier.height(6.dp))
                Text(s.ddayValue.orEmpty(), style = WidgetType.number(p), maxLines = 1)
            }
            PaperTags(p, listOfNotNull(s.tagLeave, s.tagRank))
        }
        Spacer(GlanceModifier.defaultWeight())
        if (s.progress != null) ServiceProgress(p, s) else s.ddayFoot?.let { Caption(it, p.sub) }
    }
}

class DdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DdayWidget()
}

// ── 보고싶어(누르면 바로 보냄) ────────────────────────────────────────────

class MissYouWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        val sent = s?.justSent(System.currentTimeMillis()) == true
        provideContent {
            GlanceTheme {
                // 연결 전에는 보낼 사람이 없다 — 앱을 연다.
                Card(p, if (s?.linked == true) actionRunCallback<SendMissYouAction>() else context.openApp()) {
                    Column(GlanceModifier.fillMaxSize()) {
                        s?.partnerName?.let {
                            Caption("${it}에게", p.sub)
                            Spacer(GlanceModifier.height(6.dp))
                            Text(if (sent) "$it 폰이 울렸어요" else "지금 말해 줄까요?", style = WidgetType.hand(p.ink), maxLines = 2)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MissYouFab(p, sent) }
                        val foot = when {
                            sent -> "방금 보냈어요"
                            s != null && s.missYouSentToday > 0 -> "오늘 ${s.missYouSentToday}번 보냈어요"
                            else -> null
                        }
                        foot?.let {
                            Spacer(GlanceModifier.height(8.dp))
                            Text(it, style = WidgetType.caption(p.sub).copy(textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class MissYouWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MissYouWidget()
}

/** Android 위젯 콜백은 앱 프로세스에서 돈다 → 일반 쓰기 경로(Outbox)로 보낸다. "보냈어요" 상태는 스냅샷이 다시 써지면 그린다. */
class SendMissYouAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlobalContext.getOrNull()?.get<MissYouRepository>()?.send()
        MissYouWidget().updateAll(context)
    }
}

// ── 다음 만남 ────────────────────────────────────────────────────────────

class NextMeetingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        provideContent {
            GlanceTheme {
                Card(p, context.openUri("kkotsin://calendar")) {
                    Column(GlanceModifier.fillMaxSize()) {
                        Caption("다음 만남", p.sub)
                        val value = s?.nextEventValue ?: return@Column
                        Spacer(GlanceModifier.height(8.dp))
                        Text(value, style = WidgetType.number(p), maxLines = 1)
                        Spacer(GlanceModifier.defaultWeight())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(p.event(s.nextEventType))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(s.nextEventLine.orEmpty(), style = WidgetType.hand(p.ink), maxLines = 1)
                        }
                        s.nextEventWhen?.let {
                            Spacer(GlanceModifier.height(4.dp))
                            Caption(it, p.sub)
                        }
                    }
                }
            }
        }
    }
}

class NextMeetingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextMeetingWidget()
}

// ── 상대 상태(홈 v2 에서 빠져 위젯에만 있다) ─────────────────────────────────

class PartnerStatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        provideContent {
            GlanceTheme {
                Card(p, context.openApp()) {
                    Column(GlanceModifier.fillMaxSize()) {
                        val caption = s?.partnerStatusCaption ?: return@Column
                        Caption(caption, p.sub)
                        val status = s.partnerStatus ?: return@Column
                        Spacer(GlanceModifier.height(6.dp))
                        Text(status, style = WidgetType.hand(p.ink, 26.sp), maxLines = 1)
                        Spacer(GlanceModifier.defaultWeight())
                        s.partnerStatusUntil?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(p.status(s.partnerStatusColor))
                                Spacer(GlanceModifier.width(6.dp))
                                Caption(it, p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

class PartnerStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PartnerStatusWidget()
}

// ── 한눈에 보기(큰 4×4) ──────────────────────────────────────────────────

class OverviewWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        provideContent {
            GlanceTheme {
                Card(p, context.openApp(), padding = 20) {
                    Column(GlanceModifier.fillMaxSize()) {
                        s?.ddayValue?.let { value ->
                            Caption(s.ddayCaption.orEmpty(), p.sub)
                            Spacer(GlanceModifier.height(6.dp))
                            Text(value, style = WidgetType.number(p, 52.sp), maxLines = 1)
                            Spacer(GlanceModifier.height(12.dp))
                            if (s.progress != null) ServiceProgress(p, s) else s.ddayFoot?.let { Caption(it, p.sub) }
                            val tags = listOfNotNull(s.tagLeave, s.tagMeet, s.tagRank)
                            if (tags.isNotEmpty()) {
                                Spacer(GlanceModifier.height(18.dp))
                                PaperTags(p, tags)
                            }
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(p.line)) {}
                        Spacer(GlanceModifier.height(16.dp))
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val letter = s?.letterLine
                            Row(
                                GlanceModifier.defaultWeight().clickable(s?.letterId?.let { context.openUri("kkotsin://letter/$it") } ?: context.openApp()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (letter != null) {
                                    Image(ImageProvider(R.drawable.widget_ic_mail), contentDescription = null, modifier = GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(p.accent))
                                    Spacer(GlanceModifier.width(10.dp))
                                    Column {
                                        Text(letter, style = WidgetType.label(p.ink), maxLines = 1)
                                        Spacer(GlanceModifier.height(2.dp))
                                        Caption(s.letterMeta.orEmpty(), p.sub)
                                    }
                                }
                            }
                            if (s?.linked == true) {
                                Box(GlanceModifier.clickable(actionRunCallback<SendMissYouAction>())) { MissYouFab(p, s.justSent(System.currentTimeMillis())) }
                            }
                        }
                    }
                }
            }
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}

// ── 받은 그림(작게 2×2 · 중간 4×2) ──────────────────────────────────────

/** 위젯은 애니메이션이 안 되므로 완성본 + "▶". 누르면 그 편지가 열려 리플레이를 볼 수 있다. */
class DrawingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = WidgetPalette(s)
        // 완성 PNG 가 있으면 그대로, 없으면(옛 그림) 문서를 그 자리에서 그린다.
        val bitmap: Bitmap? = s?.drawingPngPath?.let { android.graphics.BitmapFactory.decodeFile(it) }
            ?: s?.drawingDocPath?.let { DrawingBitmap.render(it, widthPx = 680) }
        provideContent {
            GlanceTheme {
                val open = s?.drawingLetterId?.let { context.openUri("kkotsin://letter/$it") } ?: context.openApp()
                val frame = p.frame(s?.widgetFrame)
                val pad = frame?.second ?: (16.dp to 16.dp)
                Box(
                    GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(frame?.first ?: p.surface)
                        .padding(start = pad.first, end = pad.first, top = pad.first, bottom = pad.second).clickable(open),
                ) {
                    when {
                        bitmap == null -> Caption("받은 그림", p.sub)
                        LocalSize.current.width >= MEDIUM.width -> DrawingMedium(p, s, bitmap)
                        else -> DrawingSmall(p, s, bitmap)
                    }
                }
            }
        }
    }
}

/** 테이프로 붙인 그림(보여 주기만 하는 물건). Glance 는 기울일 수 없어 반듯하게 붙인다. */
@Composable
private fun TapedDrawing(p: WidgetPalette, bitmap: Bitmap, modifier: GlanceModifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Image(ImageProvider(bitmap), contentDescription = "받은 그림", modifier = GlanceModifier.fillMaxSize().padding(top = 6.dp), contentScale = ContentScale.Fit)
        Box(GlanceModifier.width(40.dp).height(14.dp).background(p.tape)) {}
    }
}

@Composable
private fun DrawingSmall(p: WidgetPalette, s: WidgetSnapshot?, bitmap: Bitmap) {
    Column(GlanceModifier.fillMaxSize()) {
        TapedDrawing(p, bitmap, GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.widget_ic_play), contentDescription = null, modifier = GlanceModifier.size(10.dp), colorFilter = ColorFilter.tint(p.sub))
            Spacer(GlanceModifier.width(4.dp))
            Caption(s?.drawingMeta.orEmpty(), p.sub)
        }
    }
}

@Composable
private fun DrawingMedium(p: WidgetPalette, s: WidgetSnapshot?, bitmap: Bitmap) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        TapedDrawing(p, bitmap, GlanceModifier.width(130.dp).fillMaxHeight())
        Spacer(GlanceModifier.width(16.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            s?.drawingCaption?.let { Text(it, style = WidgetType.hand(p.ink), maxLines = 2) }
            Spacer(GlanceModifier.height(6.dp))
            Caption(s?.drawingMeta.orEmpty(), p.sub)
            Spacer(GlanceModifier.defaultWeight())
            Row(
                GlanceModifier.cornerRadius(999.dp).background(p.tag).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.widget_ic_play), contentDescription = null, modifier = GlanceModifier.size(10.dp), colorFilter = ColorFilter.tint(p.ink))
                Spacer(GlanceModifier.width(6.dp))
                Text("그리는 과정 보기", style = WidgetType.label(p.ink, bold = false), maxLines = 1)
            }
        }
    }
}

class DrawingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DrawingWidget()
}

/** 스냅샷이 바뀌면 앱이 부른다. */
suspend fun updateAllWidgets(context: Context) {
    DdayWidget().updateAll(context)
    MissYouWidget().updateAll(context)
    NextMeetingWidget().updateAll(context)
    PartnerStatusWidget().updateAll(context)
    OverviewWidget().updateAll(context)
    DrawingWidget().updateAll(context)
}
