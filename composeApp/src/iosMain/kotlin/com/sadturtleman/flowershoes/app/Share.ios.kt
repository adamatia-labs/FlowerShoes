package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sadturtleman.flowershoes.core.data.sync.InviteConfig
import com.sadturtleman.flowershoes.feature.pairing.InviteShare
import org.koin.compose.koinInject
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
internal actual fun rememberPlatformShare(): (String) -> Unit = remember { { text -> presentShareSheet(text) } }

private fun presentShareSheet(text: String) {
    val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    top?.presentViewController(controller, animated = true, completion = null)
}

/**
 * Swift(KakaoInviteShare, KakaoSDKShare)가 구현한다. 카카오톡이 없거나 카카오 키가 없으면
 * Swift 가 [fallbackText] 로 공유 시트를 띄운다.
 */
interface IosInviteShare {
    fun share(templateId: Long, args: Map<String, String>, fallbackText: String)
}

private var inviteShare: IosInviteShare? = null

/** iosApp(KkotsinApp)이 시작할 때 넣는다. */
fun setInviteShare(share: IosInviteShare) {
    inviteShare = share
}

@Composable
internal actual fun rememberInviteShare(): (InviteShare) -> Unit {
    val config = koinInject<InviteConfig>()
    return remember(config) {
        { invite ->
            val templateId = config.kakaoTemplateId
            val kakao = inviteShare
            if (templateId != null && kakao != null) kakao.share(templateId, invite.templateArgs(), inviteText(invite))
            else presentShareSheet(inviteText(invite))
        }
    }
}
