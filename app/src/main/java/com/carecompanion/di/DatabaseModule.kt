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
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()
    @Provides fun provideBiometricDao(db: AppDatabase): BiometricDao = db.biometricDao()
    @Provides fun provideArtPharmacyDao(db: AppDatabase): ArtPharmacyDao = db.artPharmacyDao()
    @Provides fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()
    @Provides fun provideFacilityDao(db: AppDatabase): FacilityDao = db.facilityDao()
    @Provides fun provideViralLoadHistoryDao(db: AppDatabase): ViralLoadHistoryDao = db.viralLoadHistoryDao()
    @Provides fun provideAppUserDao(db: AppDatabase): AppUserDao = db.appUserDao()
    @Provides fun provideReminderLogDao(db: AppDatabase): ReminderLogDao = db.reminderLogDao()
    @Provides fun provideEacEpisodeDao(db: AppDatabase): EacEpisodeDao = db.eacEpisodeDao()
    @Provides fun providePmtctRecordDao(db: AppDatabase): PmtctRecordDao = db.pmtctRecordDao()
}
