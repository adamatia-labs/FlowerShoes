package com.sadturtleman.flowershoes.di

import com.sadturtleman.flowershoes.tti.domain.PrintTtiShooter
import com.sadturtleman.flowershoes.tti.domain.TtiShooter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 측정 결과를 어디로 보낼지 정하는 곳.
 *
 * :tti 는 "언제 · 무엇을 재는가" 만 알고, "어디로 쏘는가" 는 앱이 정한다.
 * 수집 서버가 붙으면 이 한 줄만 그 구현으로 바꾸면 된다.
 */
@Module
@InstallIn(SingletonComponent::class)
object TtiAppModule {

    @Provides
    @Singleton
    fun provideTtiShooter(): TtiShooter = PrintTtiShooter()
}
