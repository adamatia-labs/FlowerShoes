package com.sadturtleman.flowershoes.common.presentation.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/**
 * 앱 공용 이미지 로더. Application 이 Coil 싱글턴으로 등록한다.
 *
 * API 용 OkHttpClient(:common:network)를 재사용하지 않고 따로 둔다 —
 * 그쪽에 인증 헤더 같은 인터셉터가 붙으면 외부 이미지 요청에까지 실려 나가기 때문이다.
 */
fun flowerImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory()) }
        .crossfade(true)
        .build()
