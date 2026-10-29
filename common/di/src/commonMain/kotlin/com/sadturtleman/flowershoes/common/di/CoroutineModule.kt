package com.sadturtleman.flowershoes.common.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** 디스패처 한정자. 계측 모듈이 IO 에서 기록하고, 테스트는 이것을 갈아 끼운다. */
val IoDispatcher = named("io")
val MainDispatcher = named("main")
val DefaultDispatcher = named("default")

val coroutineModule = module {
    single(IoDispatcher) { Dispatchers.IO }
    single(DefaultDispatcher) { Dispatchers.Default }
    single(MainDispatcher) { Dispatchers.Main }
}
