package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import java.util.Date

/**
 * One HIV-exposed infant in the EID worklist (synced from WINCO /api/art/eid/worklist), with high-risk
 * prediction and the status of each intervention (ARV prophylaxis, EID PCR + result, CTX, 18-mo
 * antibody). Keyed by the mother's [motherPersonUuid]. Self-contained (carries name) — no FK required.
 */
@Entity(
    tableName = "infant_record",
    primaryKeys = ["infantUuid"],
    indices = [Index(value = ["motherPersonUuid"])]
)
data class InfantRecord(
    val infantUuid: String,
    val name: String? = null,
    val hospitalNumber: String? = null,
    val motherPersonUuid: String? = null,
    val ancNo: String? = null,
    val dateOfDelivery: Date? = null,
    val ageWeeks: Int? = null,
    val ageMonths: Int? = null,
    val highRisk: Boolean = false,
    val highRiskReason: String? = null,
    val arvGiven: Boolean = false,
    val ctxGiven: Boolean = false,
    val pcrDone: Boolean = false,
    val pcrResult: String? = null,
    val pcrPositive: Boolean = false,
    val pcrResultReceived: Boolean = false,
    val antibodyDone: Boolean = false,
    val outcome18m: String? = null,
    val gapType: String? = null,        // primary (highest-severity) gap
    val gapSeverity: String? = null,
    val gapMessage: String? = null,
    val gapCount: Int = 0,
    val lastSyncDate: Date,
)
