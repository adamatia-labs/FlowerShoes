package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable

/** 플랫폼 공유 시트(카카오톡 등으로 초대 코드 보내기). Android: ACTION_SEND, iOS: UIActivityViewController. */
@Composable
internal expect fun rememberPlatformShare(): (String) -> Unit
