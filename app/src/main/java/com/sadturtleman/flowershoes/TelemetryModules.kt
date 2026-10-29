package com.sadturtleman.flowershoes

import android.content.Context
import com.sadturtleman.flowershoes.common.datastore.dataStoreModule
import com.sadturtleman.flowershoes.common.di.coroutineModule
import com.sadturtleman.flowershoes.common.util.device.AppBuildVariant
import com.sadturtleman.flowershoes.common.util.device.deviceInfoModule
import com.sadturtleman.flowershoes.core.featureflag.AppEnvironment
import com.sadturtleman.flowershoes.core.featureflag.FirebaseRemoteConfigSource
import com.sadturtleman.flowershoes.core.featureflag.featureFlagModule
import com.sadturtleman.flowershoes.core.logging.bizLogModule
import com.sadturtleman.flowershoes.core.logging.firebaseAnalyticsSink
import com.sadturtleman.flowershoes.feature.settings.PlatformActions
import com.sadturtleman.flowershoes.jank.data.jankDataModule
import com.sadturtleman.flowershoes.tti.data.ttiDataModule
import com.sadturtleman.flowershoes.tti.domain.PrintTtiShooter
import com.sadturtleman.flowershoes.tti.domain.TtiShooter
import com.sadturtleman.flowershoes.widget.AndroidPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 안드로이드 계측(TTI · jank · 로깅 · 피처 플래그) · 기기 정보 · 플랫폼 기능.
 * BuildConfig 를 아는 것은 이 모듈뿐이라 빌드 변형 · 환경은 여기서 꽂는다.
 *
 * [firebase]: Firebase 가 초기화됐고(google-services.json) 로컬 에뮬레이터 빌드가 아니다.
 * 아니면 로깅은 표준 출력, 플래그는 샘플 값이다.
 */
fun telemetryModules(context: Context, firebase: Boolean): List<Module> = listOf(
    coroutineModule,
    dataStoreModule,
    deviceInfoModule,
    ttiDataModule,
    jankDataModule,
    // 디버그 기록은 GA4 에 보내지 않는다.
    bizLogModule { if (firebase && !BuildConfig.DEBUG) firebaseAnalyticsSink() else null },
    featureFlagModule(AppEnvironment.of(BuildConfig.APP_ENV)) { if (firebase) FirebaseRemoteConfigSource() else null },
    module {
        single { AppBuildVariant(buildType = BuildConfig.BUILD_TYPE, flavor = BuildConfig.FLAVOR) }
        // 전송할 수집 서버가 없어 표준 출력으로 흘린다.
        single<TtiShooter> { PrintTtiShooter() }
        single<PlatformActions> { AndroidPlatformActions(context) }
    },
)
