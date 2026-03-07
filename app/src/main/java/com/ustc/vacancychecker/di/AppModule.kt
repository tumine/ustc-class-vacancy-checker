package com.ustc.vacancychecker.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt 应用级 DI 模块
 * 
 * CredentialsManager 通过 @Singleton + @Inject constructor 自动注入，
 * 无需在此模块中额外声明 @Provides
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
