package com.sadturtleman.flowershoes.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sadturtleman.flowershoes.MainActivity
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.kkColors
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import com.sadturtleman.flowershoes.core.widget.SnapshotStore
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshot
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext

/**
 * 홈 화면 위젯(설계 8장). 위젯은 DB 를 읽지 않고 스냅샷 파일만 읽는다.
 * 색은 기기 테마를 따르고(스냅샷에 테마가 들어 있다), 라이트/다크는 시스템을 따른다. 하트는 어느 테마든 로즈.
 */
internal fun snapshotStore(context: Context) =
    SnapshotStore(context.filesDir.resolve("widget/snapshot.json").absolutePath.toPath())

private fun ThemeAccentOf(s: WidgetSnapshot?) = s?.theme?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() } ?: ThemeAccent.ROSE

private class Palette(theme: ThemeAccent) {
    val light: KkColors = kkColors(theme, dark = false)
    val dark: KkColors = kkColors(theme, dark = true)
    fun of(pick: (KkColors) -> Color) = ColorProvider(day = pick(light), night = pick(dark))
}

/** 모든 위젯이 같은 틀: 둥근 표면 + 누르면 앱 열기. */
@Composable
private fun Card(p: Palette, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(p.of { it.bgSurface }).padding(14.dp).clickable(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

// ── 전역 D-day ──────────────────────────────────────────────────────────

class DdayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(ThemeAccentOf(s))
        provideContent {
            GlanceTheme {
                Card(p, actionStartActivity<MainActivity>()) {
                    Column {
                        Text("전역까지", style = TextStyle(color = p.of { it.textSecondary }, fontSize = 13.sp))
                        Spacer(GlanceModifier.height(4.dp))
                        val d = s?.daysLeft
                        Text(
                            when {
                                d == null -> "입대일을 넣어 주세요"
                                d > 0 -> "D-$d"
                                d == 0 -> "D-DAY"
                                else -> "전역 +${-d}"
                            },
                            style = TextStyle(color = p.of { it.textPrimary }, fontSize = if (d == null) 14.sp else 34.sp, fontWeight = FontWeight.Bold),
                        )
                        s?.progress?.let {
                            Spacer(GlanceModifier.height(6.dp))
                            Text("${(it * 1000).toInt() / 10.0}% · ${s.dischargeDate.orEmpty()}", style = TextStyle(color = p.of { c -> c.textMilitary }, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

class DdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DdayWidget()
}

// ── 보고싶어(누르면 바로 보냄) ────────────────────────────────────────────

class MissYouWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(ThemeAccentOf(s))
        provideContent {
            GlanceTheme {
                // "마음" 요소라 테마와 무관하게 로즈.
                Box(
                    GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(p.of { it.heart }).padding(14.dp)
                        .clickable(actionRunCallback<SendMissYouAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(ImageProvider(com.sadturtleman.flowershoes.R.drawable.ic_heart_white), contentDescription = null)
                        Text("보고싶어", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        if ((s?.missYouFromPartnerToday ?: 0) > 0) {
                            Text("오늘 받은 ${s!!.missYouFromPartnerToday}번", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 11.sp))
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

/** Android 위젯 콜백은 앱 프로세스에서 돈다 → 일반 쓰기 경로(Outbox)로 보낸다. */
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
        val p = Palette(ThemeAccentOf(s))
        provideContent {
            GlanceTheme {
                Card(p, actionStartActivity<MainActivity>()) {
                    Column {
                        Text("다음 만남", style = TextStyle(color = p.of { it.textSecondary }, fontSize = 13.sp))
                        Spacer(GlanceModifier.height(4.dp))
                        val days = s?.nextEventDays
                        if (days == null) {
                            Text("아직 잡힌 일정이 없어요", style = TextStyle(color = p.of { it.textTertiary }, fontSize = 13.sp))
                        } else {
                            Text(if (days == 0) "오늘" else "D-$days", style = TextStyle(color = p.of { it.textBrand }, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                            Text(s.nextEventTitle.orEmpty(), style = TextStyle(color = p.of { it.textPrimary }, fontSize = 13.sp), maxLines = 1)
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

// ── 받은 그림 ────────────────────────────────────────────────────────────

/** 위젯은 애니메이션이 안 되므로 완성본 + "▶ 과정". 누르면 앱이 열린다(받은 편지로 이동은 셸 딥링크가 붙으면). */
class DrawingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(ThemeAccentOf(s))
        val bitmap: Bitmap? = s?.drawingDocPath?.let { DrawingBitmap.render(it, widthPx = 680) }
        provideContent {
            GlanceTheme {
                Card(p, actionStartActivity<MainActivity>()) {
                    if (bitmap == null) {
                        Column {
                            Text("받은 그림", style = TextStyle(color = p.of { it.textSecondary }, fontSize = 13.sp))
                            Spacer(GlanceModifier.height(4.dp))
                            Text("연인이 그림을 보내면 여기에 보여요", style = TextStyle(color = p.of { it.textTertiary }, fontSize = 13.sp))
                        }
                    } else {
                        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                            Image(ImageProvider(bitmap), contentDescription = "받은 그림", modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp), contentScale = ContentScale.Fit)
                            Text("▶ 과정", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 11.sp),
                                modifier = GlanceModifier.cornerRadius(10.dp).background(ColorProvider(Color(0x99000000), Color(0x99000000))).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
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
    DrawingWidget().updateAll(context)
}
