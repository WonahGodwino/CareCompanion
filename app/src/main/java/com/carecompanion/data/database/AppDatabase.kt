package com.carecompanion.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carecompanion.data.database.dao.*
import com.carecompanion.data.database.entities.*

@Database(entities=[Patient::class,Biometric::class,ArtPharmacy::class,SyncLog::class,Facility::class,ViralLoadHistory::class,AppUser::class,ReminderLog::class,EacEpisode::class,PmtctRecord::class,InfantRecord::class],version=21,exportSchema=false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun biometricDao(): BiometricDao
    abstract fun artPharmacyDao(): ArtPharmacyDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun facilityDao(): FacilityDao
    abstract fun viralLoadHistoryDao(): ViralLoadHistoryDao
    abstract fun appUserDao(): AppUserDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun eacEpisodeDao(): EacEpisodeDao
    abstract fun pmtctRecordDao(): PmtctRecordDao
    abstract fun infantRecordDao(): InfantRecordDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `facility` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `facilityCode` TEXT, `state` TEXT, `lga` TEXT, `isActive` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add currentStatus column to track ART/PrEP/HTS/HIVST enrollment status
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `currentStatus` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Align art_pharmacy with hiv_art_pharmacy server schema
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `uuid` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `visitId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `visitType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `createdDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `createdBy` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `lastModifiedDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `lastModifiedBy` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `facilityId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `archived` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `deliveryPoint` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `dsdModelType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `ipt` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `iptType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `isDevolve` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `ardScreened` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `adverseDrugReactions` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `prescriptionError` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `refill` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `refillType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `source` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `sourceId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `rawPayload` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `latitude` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `longitude` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `art_pharmacy` ADD COLUMN `extra` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Align biometric with emr_biometric server schema
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `archived` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `count` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `createdBy` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `createdDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `extra` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `facilityId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `hashed` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `lastModifiedBy` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `lastModifiedDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `matchBiometricId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `matchPersonUuid` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `matchType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `rawPayload` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `reason` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `recaptureMessage` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `replaceDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `biometric` ADD COLUMN `sourceId` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Persist latest HIV status_date from WINCO list payload.
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `currentStatusDate` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Store ART initiation date for offline VL baseline eligibility calculation.
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `artStartDate` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add viral load and TB screening fields from WINCO EMR sync for offline eligibility calculation.
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `lastViralLoadDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `lastViralLoadResult` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `lastViralLoadResultRaw` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `lastTbScreeningDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `lastTbScreeningStatus` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `viral_load_history` (
                        `personUuid` TEXT NOT NULL,
                        `testId` INTEGER NOT NULL,
                        `sampleTypeId` INTEGER,
                        `sampleNumber` TEXT,
                        `resultRaw` TEXT,
                        `resultNumeric` INTEGER,
                        `resultDate` INTEGER,
                        `assayedDate` INTEGER,
                        `sampleDate` INTEGER,
                        `sourceId` INTEGER,
                        `source` TEXT,
                        `lastSyncDate` INTEGER NOT NULL,
                        PRIMARY KEY(`personUuid`, `testId`),
                        FOREIGN KEY(`personUuid`) REFERENCES `patient_person`(`uuid`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_viral_load_history_personUuid` ON `viral_load_history` (`personUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_viral_load_history_resultDate` ON `viral_load_history` (`resultDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_viral_load_history_sampleDate` ON `viral_load_history` (`sampleDate`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Persist NDR match outcome used by biometric recapture eligibility rules.
                db.execSQL("ALTER TABLE `patient_person` ADD COLUMN `ndrMatchedStatus` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Speed up hash-first biometric verification and identification.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_biometric_hashed` ON `biometric` (`hashed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_biometric_hashed_archived` ON `biometric` (`hashed`, `archived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_biometric_hashed_facilityId` ON `biometric` (`hashed`, `facilityId`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Local user authentication table — no internet required to sign in.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_user` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `hashedPin` TEXT NOT NULL,
                        `salt` TEXT NOT NULL,
                        `facilityId` INTEGER NOT NULL DEFAULT 0,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `lastLoginAt` INTEGER,
                        `syncedFromWinco` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_user_username` ON `app_user` (`username`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Audit log of reminders the AI actually sent (closes the measurement loop).
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminder_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `personUuid` TEXT NOT NULL,
                        `hospitalNumber` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `reminderType` TEXT NOT NULL,
                        `channels` TEXT NOT NULL,
                        `success` INTEGER NOT NULL,
                        `detail` TEXT,
                        `riskScore` INTEGER NOT NULL,
                        `riskBand` TEXT NOT NULL,
                        `appointmentDate` INTEGER,
                        `sentAt` INTEGER NOT NULL,
                        `auto` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // VL type categorisation: store the EMR VIRAL_LOAD_INDICATION code + resolved category on
        // each VL history row (Baseline/Routine/Post-EAC/PMTCT/…) for cascade tracking + the AI.
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `viral_load_history` ADD COLUMN `viralLoadIndication` INTEGER")
                db.execSQL("ALTER TABLE `viral_load_history` ADD COLUMN `vlCategory` TEXT")
            }
        }

        // EAC episodes for on-device cascade gap detection (EacGapEngine).
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `eac_episode` (
                        `personUuid` TEXT NOT NULL,
                        `episodeUuid` TEXT NOT NULL,
                        `status` TEXT,
                        `stage` TEXT,
                        `sessions` INTEGER NOT NULL,
                        `triggerVl` REAL,
                        `triggerDate` INTEGER,
                        `repeatVl` REAL,
                        `regimenSwitched` INTEGER NOT NULL,
                        `lastSyncDate` INTEGER NOT NULL,
                        PRIMARY KEY(`personUuid`, `episodeUuid`),
                        FOREIGN KEY(`personUuid`) REFERENCES `patient_person`(`uuid`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_eac_episode_personUuid` ON `eac_episode` (`personUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_eac_episode_triggerDate` ON `eac_episode` (`triggerDate`)")
            }
        }

        // PMTCT worklist (currently-pregnant women + PMTCT VL gaps).
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pmtct_record` (
                        `personUuid` TEXT NOT NULL,
                        `ancNo` TEXT NOT NULL,
                        `name` TEXT,
                        `hospitalNumber` TEXT,
                        `lmp` INTEGER,
                        `edd` INTEGER,
                        `gaWeeks` INTEGER,
                        `currentlyPregnant` INTEGER NOT NULL,
                        `pmtctVlDone` INTEGER NOT NULL,
                        `txCurr` INTEGER NOT NULL,
                        `gapType` TEXT,
                        `gapSeverity` TEXT,
                        `gapMessage` TEXT,
                        `lastSyncDate` INTEGER NOT NULL,
                        PRIMARY KEY(`personUuid`, `ancNo`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pmtct_record_personUuid` ON `pmtct_record` (`personUuid`)")
            }
        }

        // EID worklist (HIV-exposed infants + high-risk + intervention gaps).
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `infant_record` (
                        `infantUuid` TEXT NOT NULL,
                        `name` TEXT,
                        `hospitalNumber` TEXT,
                        `motherPersonUuid` TEXT,
                        `ancNo` TEXT,
                        `dateOfDelivery` INTEGER,
                        `ageWeeks` INTEGER,
                        `ageMonths` INTEGER,
                        `highRisk` INTEGER NOT NULL,
                        `highRiskReason` TEXT,
                        `arvGiven` INTEGER NOT NULL,
                        `ctxGiven` INTEGER NOT NULL,
                        `pcrDone` INTEGER NOT NULL,
                        `pcrResult` TEXT,
                        `pcrPositive` INTEGER NOT NULL,
                        `pcrResultReceived` INTEGER NOT NULL,
                        `antibodyDone` INTEGER NOT NULL,
                        `outcome18m` TEXT,
                        `gapType` TEXT,
                        `gapSeverity` TEXT,
                        `gapMessage` TEXT,
                        `gapCount` INTEGER NOT NULL,
                        `lastSyncDate` INTEGER NOT NULL,
                        PRIMARY KEY(`infantUuid`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_infant_record_motherPersonUuid` ON `infant_record` (`motherPersonUuid`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"care_companion_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}