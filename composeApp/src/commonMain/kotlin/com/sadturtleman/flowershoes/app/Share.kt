package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import com.sadturtleman.flowershoes.feature.pairing.InviteShare

/** 플랫폼 공유 시트(텍스트). Android: ACTION_SEND, iOS: UIActivityViewController. */
@Composable
internal expect fun rememberPlatformShare(): (String) -> Unit

/**
 * 초대 링크 보내기(#15 결정 7): 카카오톡이 있고 피드 템플릿이 설정돼 있으면 카카오 사용자 정의 템플릿
 * (인자 `name` · `token`, 버튼은 `kakao{앱키}://kakaolink?invite=${token}` · 웹 `/i/${token}`), 아니면 OS 공유 시트로 [inviteText].
 */
@Composable
internal expect fun rememberInviteShare(): (InviteShare) -> Unit

/** 카카오톡이 없을 때 OS 공유 시트로 보내는 글. 링크를 열면 OG 미리보기(보낸 사람 이름)가 뜬다. */
internal fun inviteText(invite: InviteShare) = "꽃신에서 나랑 연결해 줘! ${invite.url}"

/** 카카오 템플릿 인자. 프로필 이름이 비어 있으면 보낸 사람은 "연인". */
internal fun InviteShare.templateArgs(): Map<String, String> = mapOf("name" to name.ifBlank { "연인" }, "token" to token)
