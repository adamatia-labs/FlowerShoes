package com.sadturtleman.flowershoes.home.data

import com.sadturtleman.flowershoes.home.domain.HomeContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HomeDataModule {

    @Binds
    @Singleton
    abstract fun bindHomeContentRepository(
        impl: HomeContentRepositoryImpl,
    ): HomeContentRepository
}
