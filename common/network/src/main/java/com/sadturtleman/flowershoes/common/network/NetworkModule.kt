package com.sadturtleman.flowershoes.common.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 앱 공용 통신 스택.
 *
 * OkHttp · Retrofit · JSON 변환까지만 책임진다.
 * 어떤 엔드포인트가 있는지(API 인터페이스)는 이 Retrofit 을 주입받는 data 모듈이 정한다.
 *
 * 주소는 소스에 두지 않는다. `local.properties` 의 `FLOWERSHOES_BASE_URL` 을 빌드 시
 * [BuildConfig] 로 굽는다. 인증 헤더가 필요해지면 인터셉터를 여기에 한 줄 더한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // 서버가 필드를 늘려도 앱이 깨지지 않게 모르는 키는 버린다.
        ignoreUnknownKeys = true
        // null 로 온 값은 DTO 의 기본값으로 채운다.
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
            }
        }
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    private const val TIMEOUT_SECONDS = 15L
    private const val JSON_MEDIA_TYPE = "application/json; charset=UTF-8"
}
