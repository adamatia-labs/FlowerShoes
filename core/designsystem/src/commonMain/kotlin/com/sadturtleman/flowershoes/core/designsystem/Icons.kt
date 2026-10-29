package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.ic_album
import com.sadturtleman.flowershoes.core.designsystem.res.ic_back
import com.sadturtleman.flowershoes.core.designsystem.res.ic_calendar
import com.sadturtleman.flowershoes.core.designsystem.res.ic_check
import com.sadturtleman.flowershoes.core.designsystem.res.ic_chevron_down
import com.sadturtleman.flowershoes.core.designsystem.res.ic_chevron_right
import com.sadturtleman.flowershoes.core.designsystem.res.ic_chevron_up
import com.sadturtleman.flowershoes.core.designsystem.res.ic_clock
import com.sadturtleman.flowershoes.core.designsystem.res.ic_close
import com.sadturtleman.flowershoes.core.designsystem.res.ic_eraser
import com.sadturtleman.flowershoes.core.designsystem.res.ic_heart
import com.sadturtleman.flowershoes.core.designsystem.res.ic_highlighter
import com.sadturtleman.flowershoes.core.designsystem.res.ic_home
import com.sadturtleman.flowershoes.core.designsystem.res.ic_mail
import com.sadturtleman.flowershoes.core.designsystem.res.ic_pen
import com.sadturtleman.flowershoes.core.designsystem.res.ic_photo
import com.sadturtleman.flowershoes.core.designsystem.res.ic_redo
import com.sadturtleman.flowershoes.core.designsystem.res.ic_send
import com.sadturtleman.flowershoes.core.designsystem.res.ic_settings
import com.sadturtleman.flowershoes.core.designsystem.res.ic_social_apple
import com.sadturtleman.flowershoes.core.designsystem.res.ic_social_google
import com.sadturtleman.flowershoes.core.designsystem.res.ic_social_kakao
import com.sadturtleman.flowershoes.core.designsystem.res.ic_spinner
import com.sadturtleman.flowershoes.core.designsystem.res.ic_sticker
import com.sadturtleman.flowershoes.core.designsystem.res.ic_toast_error
import com.sadturtleman.flowershoes.core.designsystem.res.ic_toast_info
import com.sadturtleman.flowershoes.core.designsystem.res.ic_toast_success
import com.sadturtleman.flowershoes.core.designsystem.res.ic_trace
import com.sadturtleman.flowershoes.core.designsystem.res.ic_undo
import com.sadturtleman.flowershoes.core.designsystem.res.ic_camera
import com.sadturtleman.flowershoes.core.designsystem.res.ic_download
import com.sadturtleman.flowershoes.core.designsystem.res.ic_pause
import com.sadturtleman.flowershoes.core.designsystem.res.ic_play
import com.sadturtleman.flowershoes.core.designsystem.res.ic_replay
import com.sadturtleman.flowershoes.core.designsystem.res.ic_link
import com.sadturtleman.flowershoes.core.designsystem.res.ic_cloud
import com.sadturtleman.flowershoes.core.designsystem.res.logo_mark
import com.sadturtleman.flowershoes.core.designsystem.res.ic_note
import com.sadturtleman.flowershoes.core.designsystem.res.ic_brush
import com.sadturtleman.flowershoes.core.designsystem.res.ic_more
import com.sadturtleman.flowershoes.core.designsystem.res.ic_plus
import com.sadturtleman.flowershoes.core.designsystem.res.ic_alert
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Figma 🧩 Components 의 Icon 세트(24px, 1.8 선). 색은 [KkIcon] 의 tint 로 바꾼다. */
enum class KkIcons(internal val res: DrawableResource) {
    Home(Res.drawable.ic_home),
    Calendar(Res.drawable.ic_calendar),
    Mail(Res.drawable.ic_mail),
    Heart(Res.drawable.ic_heart),
    Clock(Res.drawable.ic_clock),
    Send(Res.drawable.ic_send),
    Album(Res.drawable.ic_album),
    Pen(Res.drawable.ic_pen),
    Highlighter(Res.drawable.ic_highlighter),
    Eraser(Res.drawable.ic_eraser),
    Undo(Res.drawable.ic_undo),
    Download(Res.drawable.ic_download),
    Camera(Res.drawable.ic_camera),
    Play(Res.drawable.ic_play),
    Pause(Res.drawable.ic_pause),
    Replay(Res.drawable.ic_replay),
    Redo(Res.drawable.ic_redo),
    Sticker(Res.drawable.ic_sticker),
    Photo(Res.drawable.ic_photo),
    Trace(Res.drawable.ic_trace),
    Settings(Res.drawable.ic_settings),
    ChevronRight(Res.drawable.ic_chevron_right),
    Link(Res.drawable.ic_link),
    Cloud(Res.drawable.ic_cloud),
    Note(Res.drawable.ic_note),
    Brush(Res.drawable.ic_brush),
    More(Res.drawable.ic_more),
    Plus(Res.drawable.ic_plus),
    Alert(Res.drawable.ic_alert),

    // 컴포넌트 안에서만 쓰는 글리프(크기가 24 가 아니다 — 자리에 맞춰 가운데 둔다)
    Back(Res.drawable.ic_back),
    Close(Res.drawable.ic_close),
    Check(Res.drawable.ic_check),
    ChevronDown(Res.drawable.ic_chevron_down),
    ChevronUp(Res.drawable.ic_chevron_up),
    ToastInfo(Res.drawable.ic_toast_info),
    ToastSuccess(Res.drawable.ic_toast_success),
    ToastError(Res.drawable.ic_toast_error),
    Spinner(Res.drawable.ic_spinner),

    // 소셜 로그인 공식 로고(색이 정해져 있어 tint 없이 쓴다)
    SocialKakao(Res.drawable.ic_social_kakao),
    SocialApple(Res.drawable.ic_social_apple),
    SocialGoogle(Res.drawable.ic_social_google),
}

/** 한 색 아이콘. [tint] 가 null 이면 SVG 원래 색(토스트 성공 · 오류처럼 색이 정해진 것). */
@Composable
fun KkIcon(icon: KkIcons, tint: Color?, modifier: Modifier = Modifier, size: Dp = 24.dp, contentDescription: String? = null) {
    Image(
        painter = painterResource(icon.res),
        contentDescription = contentDescription,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(size),
    )
}

/** 꽃신 로고(꽃 + 고무신 끈). 로그인 · 복원 화면. */
@Composable
fun KkLogoMark(modifier: Modifier = Modifier, size: Dp = 70.4.dp) {
    Image(painterResource(Res.drawable.logo_mark), contentDescription = "꽃신", modifier = modifier.size(size))
}
