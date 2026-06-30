package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import java.util.Date

/**
 * One currently-pregnant woman in the PMTCT worklist (synced from WINCO /api/art/pmtct/worklist).
 * Self-contained (carries name/hospitalNumber) so no FK to patient_person is required. Current
 * gestational age is re-derived on-device from [lmp] for display freshness; the gap is WINCO's
 * TX_CURR-gated assessment (PMTCT VL due/overdue for the 32–36 week window, code 306).
 */
@Entity(
    tableName = "pmtct_record",
    primaryKeys = ["personUuid", "ancNo"],
    indices = [Index(value = ["personUuid"])]
)
data class PmtctRecord(
    val personUuid: String,
    val ancNo: String,
    val name: String? = null,
    val hospitalNumber: String? = null,
    val lmp: Date? = null,
    val edd: Date? = null,
    val gaWeeks: Int? = null,
    val currentlyPregnant: Boolean = true,
    val pmtctVlDone: Boolean = false,
    val txCurr: Boolean = false,
    val fetalHighRisk: Boolean = false,        // baby will be high-risk → prep enhanced prophylaxis at birth
    val fetalHighRiskReason: String? = null,
    val gapType: String? = null,       // PMTCT_VL_DUE | PMTCT_VL_OVERDUE | null
    val gapSeverity: String? = null,   // critical | high
    val gapMessage: String? = null,
    val lastSyncDate: Date,
)
