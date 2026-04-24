package com.carecompanion.di

import android.content.Context
import androidx.room.Room
import com.carecompanion.data.database.AppDatabase
import com.carecompanion.data.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "care_companion_db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()
    @Provides fun provideBiometricDao(db: AppDatabase): BiometricDao = db.biometricDao()
    @Provides fun provideArtPharmacyDao(db: AppDatabase): ArtPharmacyDao = db.artPharmacyDao()
    @Provides fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()
    @Provides fun provideFacilityDao(db: AppDatabase): FacilityDao = db.facilityDao()
}