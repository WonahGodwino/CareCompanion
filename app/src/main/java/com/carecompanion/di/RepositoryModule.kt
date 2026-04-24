package com.carecompanion.di

import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.data.repository.PatientRepositoryImpl
import com.carecompanion.data.repository.SyncRepository
import com.carecompanion.data.repository.SyncRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository
    @Binds @Singleton abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}