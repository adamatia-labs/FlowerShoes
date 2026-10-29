package com.sadturtleman.flowershoes.app

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kakao.sdk.share.ShareClient
import com.sadturtleman.flowershoes.core.data.sync.InviteConfig
import com.sadturtleman.flowershoes.feature.pairing.InviteShare
import org.koin.compose.koinInject

@Composable
internal actual fun rememberPlatformShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, "초대 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** 카카오 SDK 는 로그인용 앱 키로 초기화된다(AndroidAuthGateway). 키 · 템플릿이 없거나 카카오톡이 없으면 OS 공유 시트. */
@Composable
internal actual fun rememberInviteShare(): (InviteShare) -> Unit {
    val context = LocalContext.current
    val config = koinInject<InviteConfig>()
    val text = rememberPlatformShare()
    return remember(context, config, text) {
        { invite ->
            val templateId = config.kakaoTemplateId
            val kakao = templateId != null && runCatching {
                if (!ShareClient.instance.isKakaoTalkSharingAvailable(context)) return@runCatching false
                ShareClient.instance.shareCustom(context, templateId, invite.templateArgs()) { result, error ->
                    if (result != null && error == null) {
                        context.startActivity(result.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        text(inviteText(invite))
                    }
                }
                true
            }.getOrDefault(false)
            if (!kakao) text(inviteText(invite))
        }
    }
}
