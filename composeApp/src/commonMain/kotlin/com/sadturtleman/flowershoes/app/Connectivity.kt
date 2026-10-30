package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * 지금 인터넷에 닿는가(공통 상태 · 네트워크 없음, #72). 화면이 떠 있는 동안 OS 네트워크 상태를 지켜본다.
 * Android: ConnectivityManager 기본 네트워크(인터넷 확인됨), iOS: NWPathMonitor(satisfied).
 */
@Composable
internal expect fun rememberOnline(): State<Boolean>

/** 업데이트 필요 › 업데이트하기가 여는 스토어 페이지. */
internal expect val storeUrl: String
