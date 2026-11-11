package com.sadturtleman.flowershoes.core.data

import kotlinx.coroutines.CancellationException

/** 설정 쓰기 차례를 기다리는 동안 인증 계정이 바뀌었다. 이전 로그인 완료를 취소한다. */
class AccountBindingChangedException : CancellationException("로그인 계정이 바뀌었어요")
