package com.sadturtleman.flowershoes.common.di

import javax.inject.Qualifier

/** 앱 전역에서 IO 바운드 작업에 사용하는 디스패처. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** 앱 전역에서 UI 작업에 사용하는 디스패처. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** 앱 전역에서 CPU 바운드 작업에 사용하는 디스패처. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
