package com.sadturtleman.flowershoes.logging.data

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.logging.domain.BizLogClock
import com.sadturtleman.flowershoes.logging.domain.BizLogShooter
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import com.sadturtleman.flowershoes.logging.domain.SystemBizLogClock
import com.sadturtleman.flowershoes.logging.domain.createBizLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * :logging:domain 의 포트를 조립하는 곳.
 *
 * TTI 와 달리 꽂을 안드로이드 구현이 없다 — 쌓아 두는 곳이 없고 시각은 벽시계라 순수 JVM 으로 끝난다.
 * 그래도 모듈을 따로 두는 것은 조립과 전송 구현이 있을 자리이기 때문이다.
 *
 * TTI 는 전송지를 app 이 고르게 두었지만 여기서는 그러지 않는다 —
 * 외부 수집 서비스를 붙이지 않기로 했고, 고를 것이 [PrintBizLogShooter] 하나뿐이라
 * 앱까지 끌고 올라가면 바인딩 한 줄이 모듈 하나를 더 만드는 값이 된다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BizLogDataModule {

    /** 보낼 서버가 생기면 이 한 줄이 그 구현을 가리키게 된다. */
    @Binds
    @Singleton
    abstract fun bindBizLogShooter(impl: PrintBizLogShooter): BizLogShooter

    companion object {

        @Provides
        @Singleton
        fun provideBizLogClock(): BizLogClock = SystemBizLogClock()

        /**
         * 기록기는 도메인이 만든다.
         *
         * 생성자 주입 대신 여기서 조립하는 것은 [IoDispatcher] 한정자가 안드로이드 모듈에 있어
         * 순수 코틀린인 :logging:domain 이 참조할 수 없기 때문이다.
         */
        @Provides
        @Singleton
        fun provideBizLogger(
            shooter: BizLogShooter,
            clock: BizLogClock,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): BizLogger = createBizLogger(shooter, clock, ioDispatcher)
    }
}
