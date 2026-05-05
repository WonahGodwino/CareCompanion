package com.carecompanion.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new indices for biometric table
        database.execSQL("CREATE INDEX IF NOT EXISTS index_biometric_sourceId ON biometric(sourceId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_biometric_lastSyncDate ON biometric(lastSyncDate)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_biometric_archived ON biometric(archived)")
        
        // Add indices for patient table if missing
        database.execSQL("CREATE INDEX IF NOT EXISTS index_patient_emrId ON patient(emrId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_patient_personUuid ON patient(personUuid)")

        // Add person_uuid column for WINCO sync if not present
        database.execSQL("ALTER TABLE patient_person ADD COLUMN person_uuid TEXT")
    }
}
