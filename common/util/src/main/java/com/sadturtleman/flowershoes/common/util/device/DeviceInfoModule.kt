package com.sadturtleman.flowershoes.common.util.device

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 기기 정보 제공자를 그래프에 올린다.
 *
 * 구현이 하나뿐인데도 인터페이스를 두는 이유는 테스트다 — 화면과 기록기는 기기 정보를
 * 받아 쓰기만 하므로, 그 테스트에서 저사양 기기나 절전 모드를 만들어 내려면
 * 이 계약을 가짜로 갈아 끼울 수 있어야 한다.
 *
 * [AppBuildVariant] 는 여기서 만들지 않는다. BuildConfig 는 그것을 생성한 모듈의 것이라
 * 플레이버를 가진 :app 만 읽을 수 있고, :app 이 꽂아 주지 않으면 빌드가 실패한다.
 * 조용히 기본값을 채우는 것보다 낫다 — 틀린 빌드 변형이 붙은 통계는 아무도 의심하지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeviceInfoModule {

    @Binds
    @Singleton
    abstract fun bindDeviceInfoProvider(impl: AndroidDeviceInfoProvider): DeviceInfoProvider
}
