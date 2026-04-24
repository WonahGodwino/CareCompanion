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

@Database(entities=[Patient::class,Biometric::class,ArtPharmacy::class,SyncLog::class,Facility::class],version=7,exportSchema=false)
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"care_companion_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_6_7)
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}