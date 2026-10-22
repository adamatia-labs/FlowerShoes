package com.sadturtleman.flowershoes

import android.content.Context
import com.sadturtleman.flowershoes.common.datastore.dataStoreModule
import com.sadturtleman.flowershoes.common.di.coroutineModule
import com.sadturtleman.flowershoes.common.util.device.AppBuildVariant
import com.sadturtleman.flowershoes.common.util.device.deviceInfoModule
import com.sadturtleman.flowershoes.feature.settings.PlatformActions
import com.sadturtleman.flowershoes.featureflag.data.featureFlagDataModule
import com.sadturtleman.flowershoes.featureflag.domain.AppEnvironment
import com.sadturtleman.flowershoes.jank.data.jankDataModule
import com.sadturtleman.flowershoes.logging.data.bizLogDataModule
import com.sadturtleman.flowershoes.tti.data.ttiDataModule
import com.sadturtleman.flowershoes.tti.domain.PrintTtiShooter
import com.sadturtleman.flowershoes.tti.domain.TtiShooter
import com.sadturtleman.flowershoes.widget.AndroidPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 안드로이드 전용 계측(TTI · jank · 로깅 · 피처 플래그) · 기기 정보 · 플랫폼 기능.
 * BuildConfig 를 아는 것은 이 모듈뿐이라 빌드 변형 · 환경은 여기서 꽂는다.
 */
fun telemetryModules(context: Context): List<Module> = listOf(
    coroutineModule,
    dataStoreModule,
    deviceInfoModule,
    ttiDataModule,
    jankDataModule,
    bizLogDataModule,
    featureFlagDataModule,
    module {
        single { AppBuildVariant(buildType = BuildConfig.BUILD_TYPE, flavor = BuildConfig.FLAVOR) }
        single { AppEnvironment.of(BuildConfig.APP_ENV) }
        // 전송할 수집 서버가 없어 표준 출력으로 흘린다.
        single<TtiShooter> { PrintTtiShooter() }
        single<PlatformActions> { AndroidPlatformActions(context) }
    },
)
