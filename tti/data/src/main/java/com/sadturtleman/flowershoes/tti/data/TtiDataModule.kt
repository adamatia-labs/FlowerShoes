package com.sadturtleman.flowershoes.tti.data

import android.content.Context
import androidx.room.Room
import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.tti.data.room.TtiDao
import com.sadturtleman.flowershoes.tti.data.room.TtiDatabase
import com.sadturtleman.flowershoes.tti.domain.TtiClock
import com.sadturtleman.flowershoes.tti.domain.TtiRecordStore
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.tti.domain.TtiShooter
import com.sadturtleman.flowershoes.tti.domain.createTtiRecorder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * :tti:domain 의 포트에 안드로이드 구현을 꽂는 곳.
 *
 * [TtiShooter] 는 여기서 묶지 않는다 — 어디로 보낼지는 앱이 정할 일이라 app 모듈이 바인딩한다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TtiDataModule {

    @Binds
    @Singleton
    abstract fun bindTtiRecordStore(impl: RoomTtiRecordStore): TtiRecordStore

    @Binds
    abstract fun bindTtiClock(impl: AndroidTtiClock): TtiClock

    companion object {

        /**
         * 기록기는 도메인이 만든다.
         *
         * 생성자 주입 대신 여기서 조립하는 것은 [IoDispatcher] 한정자가 안드로이드 모듈에 있어
         * 순수 코틀린인 :tti:domain 이 참조할 수 없기 때문이다.
         */
        @Provides
        @Singleton
        fun provideTtiRecorder(
            store: TtiRecordStore,
            shooter: TtiShooter,
            clock: TtiClock,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): TtiRecorder = createTtiRecorder(store, shooter, clock, ioDispatcher)

        @Provides
        @Singleton
        fun provideTtiDatabase(@ApplicationContext context: Context): TtiDatabase =
            Room.databaseBuilder(context, TtiDatabase::class.java, DATABASE_NAME)
                // 계측 데이터라 스키마가 바뀌면 마이그레이션 대신 버린다.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        @Provides
        fun provideTtiDao(database: TtiDatabase): TtiDao = database.ttiDao()

        private const val DATABASE_NAME = "tti.db"
    }
}
