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

@Database(entities=[Patient::class,Biometric::class,ArtPharmacy::class,SyncLog::class,Facility::class],version=10,exportSchema=false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun biometricDao(): BiometricDao
    abstract fun artPharmacyDao(): ArtPharmacyDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun facilityDao(): FacilityDao
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"care_companion_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}