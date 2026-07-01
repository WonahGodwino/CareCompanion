package com.carecompanion.data.risk

import android.content.Context
import com.carecompanion.data.database.dao.EacEpisodeDao
import com.carecompanion.data.database.dao.InfantRecordDao
import com.carecompanion.data.database.dao.PmtctRecordDao
import com.carecompanion.data.database.dao.ViralLoadHistoryDao
import com.carecompanion.data.database.entities.IITClient
import com.carecompanion.data.messaging.ReminderContext
import com.carecompanion.data.messaging.ReminderTarget
import com.carecompanion.data.messaging.ReminderType
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.GeoUtils
import com.carecompanion.utils.PatientRiskEngine
import com.carecompanion.utils.PatientRiskEngine.IIT_THRESHOLD_DAYS
import com.carecompanion.utils.PatientRiskEngine.PatientRiskScore
import com.carecompanion.utils.PatientRiskEngine.PatientSignals
import com.carecompanion.utils.PatientRiskEngine.RiskBand
import com.carecompanion.utils.SharedPreferencesHelper
import com.carecompanion.utils.ViralLoadEligibilityEngine
import java.util.Date
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** One scored client, ready for display or for an automated reminder. */
data class AssessedClient(
    val score: PatientRiskScore,
    val type: ReminderType,
    val target: ReminderTarget,
    val context: ReminderContext,
)

data class RiskAssessment(
    val forecast: List<AssessedClient> = emptyList(),
    val approaching: List<AssessedClient> = emptyList(),
    val established: List<AssessedClient> = emptyList(),
    /** Every scored client (incl. LOW-risk upcoming) — used by the reminder scheduler. */
    val all: List<AssessedClient> = emptyList(),
) {
    val flagged: List<AssessedClient> get() = forecast + approaching + established
}

/**
 * Single source of truth for AI patient-monitoring scoring. Shared by the UI
 * ([com.carecompanion.presentation.viewmodels.AiInsightsViewModel]) and the background
 * [com.carecompanion.data.reminder.ReminderWorker] so on-screen flags and auto-sent
 * reminders always agree.
 */
@Singleton
class RiskAssessmentService @Inject constructor(
    private val patientRepository: PatientRepository,
    private val eacEpisodeDao: EacEpisodeDao,
    private val pmtctRecordDao: PmtctRecordDao,
    private val infantRecordDao: InfantRecordDao,
    private val viralLoadHistoryDao: ViralLoadHistoryDao,
    @ApplicationContext private val context: Context,
) {
    // Cascade gaps (VL/EAC, PMTCT, EID, TB) can elevate triage even when IIT-forecast risk is low.
    private val cascadeDomains = setOf(
        PatientRiskEngine.RiskDomain.VIROLOGIC, PatientRiskEngine.RiskDomain.PMTCT,
        PatientRiskEngine.RiskDomain.EID, PatientRiskEngine.RiskDomain.TB,
    )

    private fun severityRank(s: String?): Int = when (s) { "critical" -> 0; "high" -> 1; else -> 2 }

    private val cascadeReminderTypes = setOf(
        ReminderType.PMTCT_RECALL, ReminderType.EID_RECALL, ReminderType.EAC_RECALL,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sourceFlow(): Flow<List<IITClient>> = flowOf(Unit).flatMapLatest {
        val fid = SharedPreferencesHelper.getActiveFacilityId(context)
        if (fid > 0) patientRepository.observeArtRefillClientsByFacility(fid)
        else patientRepository.observeArtRefillClients()
    }

    /** Reactive assessment for the UI. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<RiskAssessment> = sourceFlow().mapLatest { assess(it) }

    /** One-shot assessment for the background worker. */
    suspend fun assessOnce(): RiskAssessment = assess(sourceFlow().first())

    suspend fun assess(clients: List<IITClient>): RiskAssessment {
        val fid = SharedPreferencesHelper.getActiveFacilityId(context)
        val facilityName = SharedPreferencesHelper.getActiveFacilityName(context).orEmpty()
        val historyByUuid = patientRepository.getArtPharmacyForPatients(clients.map { it.uuid })
            .groupBy { it.personUuid }
        val patientByUuid = (if (fid > 0) patientRepository.getAllActiveByFacility(fid)
            else patientRepository.getAllActivePatients()).associateBy { it.uuid }
        val facilityLat = SharedPreferencesHelper.getFacilityLatitude(fid)
        val facilityLng = SharedPreferencesHelper.getFacilityLongitude(fid)
        // Biometric enrolment — reuse the app's biometric store (archived rows excluded).
        val enrolledUuids = patientRepository.getAllBiometrics()
            .filter { (it.archived ?: 0) == 0 }
            .map { it.personUuid }
            .toHashSet()

        // Cascade gaps synced on-device (small cohorts) — index by the client they belong to.
        val eacByUuid = eacEpisodeDao.getAll().groupBy { it.personUuid }
        val pmtctByUuid = pmtctRecordDao.getAll().associateBy { it.personUuid }
        val infantsByMother = infantRecordDao.getAll()
            .filter { it.motherPersonUuid != null }.groupBy { it.motherPersonUuid }

        // Adopted learned model, if any (guardrail + schema enforced in activeModel()).
        val learnedModel = ModelStore.activeModel()
        // EAC head (2nd outcome) + VL history — also feeds the rule-based virological-failure watch.
        val eacHead = ModelStore.eacModel()
        val vlByUuid = viralLoadHistoryDao.getByPersonUuids(clients.map { it.uuid }).groupBy { it.personUuid }

        val now = System.currentTimeMillis()
        val today = Date(now)
        val assessed = clients.mapNotNull { c ->
            val appt = c.nextAppointment ?: return@mapNotNull null
            val diffDays = TimeUnit.MILLISECONDS.toDays(appt.time - now).toInt()
            val daysOverdue = if (diffDays < 0) -diffDays else 0
            val daysUntil = if (diffDays >= 0) diffDays else null
            val patient = patientByUuid[c.uuid]
            val distanceKm = if (facilityLat != null && facilityLng != null)
                GeoUtils.haversineKm(
                    GeoUtils.parseCoord(patient?.latitude), GeoUtils.parseCoord(patient?.longitude),
                    facilityLat, facilityLng,
                ) else null

            // ── Reuse the app's existing clinical calculations ──────────────
            // Viral load: same eligibility engine the worklist uses.
            val vlOverdue = ViralLoadEligibilityEngine.evaluate(
                dateOfBirth = patient?.dateOfBirth ?: c.dateOfBirth,
                artRegistrationDate = patient?.artStartDate,
                dateSampleCollected = patient?.lastViralLoadDate,
                referenceDate = today,
            )?.isEligibleToday == true
            // TB screening status (from WINCO tb_screening fields on the patient row).
            val tbStatus = patient?.lastTbScreeningStatus?.trim()?.uppercase()
            val tbSymptomatic = tbStatus?.let {
                it.contains("POSITIVE") || it.contains("PRESUMPTIVE") || it.contains("ACTIVE")
            }
            val tbScreenOverdue = patient?.lastTbScreeningDate?.let {
                TimeUnit.MILLISECONDS.toDays(now - it.time) > 365
            } ?: true // never screened counts as due

            // ── Cascade gaps for this client (EAC / PMTCT / EID) ────────────
            val eacLatest = eacByUuid[c.uuid]?.maxByOrNull { it.triggerDate?.time ?: Long.MIN_VALUE }
            val pmtct = pmtctByUuid[c.uuid]
            val infants = infantsByMother[c.uuid].orEmpty()
            val topInfant = infants.filter { it.gapType != null }.minByOrNull { severityRank(it.gapSeverity) }

            val history = PatientRiskEngine.analyzeHistory(historyByUuid[c.uuid].orEmpty())
            var score = PatientRiskEngine.score(
                PatientSignals(
                    uuid = c.uuid,
                    displayName = c.displayName,
                    hospitalNumber = c.hospitalNumber,
                    initial = c.initial,
                    dateOfBirth = patient?.dateOfBirth ?: c.dateOfBirth,
                    daysOverdue = daysOverdue,
                    daysUntilAppointment = daysUntil,
                    refillPeriodDays = c.refillPeriod,
                    dsdModel = c.dsdModel,
                    history = history,
                    distanceToFacilityKm = distanceKm,
                    hasPhoneContact = patient?.phoneNumber?.isNotBlank(),
                    // Virologic / EAC — EAC sessions arrive later; null means "unknown",
                    // which for an unsuppressed client surfaces as "EAC not started".
                    lastViralLoadResult = patient?.lastViralLoadResult,
                    lastViralLoadDate = patient?.lastViralLoadDate,
                    viralLoadOverdue = vlOverdue,
                    eacSessionsCompleted = eacLatest?.sessions,
                    eacStage = eacLatest?.stage,
                    priorUnsuppressed = vlByUuid[c.uuid]?.count { (it.resultNumeric ?: 0L) >= 1000L } ?: 0,
                    // TB / TPT
                    tbScreenOverdue = tbScreenOverdue,
                    tbSymptomatic = tbSymptomatic,
                    // Identity
                    biometricEnrolled = c.uuid in enrolledUuids,
                    // PMTCT (mother)
                    pregnantOrBreastfeeding = pmtct != null,
                    pmtctVlGap = pmtct?.gapType,
                    pmtctGaWeeks = pmtct?.gaWeeks,
                    fetalHighRisk = pmtct?.fetalHighRisk ?: false,
                    // EID (exposed infant of this client)
                    exposedInfantGap = topInfant?.gapType,
                    exposedInfantHighRisk = infants.any { it.highRisk },
                    exposedInfantCount = infants.size,
                )
            )

            // Learned model drives the FORECAST decision (with heuristic fallback). The
            // heuristic factors are kept as the explanation; the same signals feed the model.
            if (learnedModel != null && score.isForecast) {
                val features = RiskFeatures.vector(
                    priorIitEpisodes = history.priorIitEpisodes,
                    priorLatePickups = history.priorLatePickups,
                    gaps = (history.totalVisits - 1).coerceAtLeast(0),
                    ageYears = DateUtils.calculateAge(patient?.dateOfBirth ?: c.dateOfBirth),
                    refillDays = c.refillPeriod ?: 0,
                    distanceKm = distanceKm,
                    hasPhone = patient?.phoneNumber?.isNotBlank() ?: false,
                    // Live VL = the client's latest result (= most recent VL before now); WINCO now
                    // serves it complete via the lab_test_id=16 catalog.
                    vlValue = patient?.lastViralLoadResult,
                )
                val p = learnedModel.probability(features)
                val pct = (p * 100).roundToInt()
                val modelFactor = PatientRiskEngine.RiskFactor(
                    PatientRiskEngine.RiskDomain.HISTORY, "AI model forecast risk: $pct%", pct,
                )
                // Unified priority = the more urgent of IIT-forecast risk and any cascade gap, so an
                // unsuppressed VL / PMTCT-VL-overdue / infant-PCR+ still elevates a low-IIT-risk client.
                val cascadeSum = score.factors.filter { it.domain in cascadeDomains }.sumOf { it.points }
                val finalScore = maxOf(pct, cascadeSum).coerceIn(0, 100)
                score = score.copy(
                    score = finalScore,
                    band = if (cascadeSum > pct) PatientRiskEngine.bandForScore(finalScore) else bandForProbability(p),
                    factors = listOf(modelFactor) + score.factors,
                )
            }

            // EAC cascade-failure head: for an unsuppressed client, forecast re-suppression failure and
            // surface it as an explainable factor that elevates triage (2nd prediction head, eac-v1).
            if (eacHead != null && (patient?.lastViralLoadResult ?: 0L) >= 1000L) {
                val priorUns = ((vlByUuid[c.uuid]?.count { (it.resultNumeric ?: 0L) >= 1000L } ?: 0) - 1)
                    .coerceAtLeast(0)
                val monthsOnArt = patient?.artStartDate?.let { ((now - it.time) / (30L * 86_400_000L)).toInt() } ?: 0
                val feat = EacFeatures.vector(
                    triggerVl = patient?.lastViralLoadResult ?: 0L,
                    monthsOnArt = monthsOnArt,
                    age = DateUtils.calculateAge(patient?.dateOfBirth ?: c.dateOfBirth),
                    priorUnsuppressed = priorUns,
                    nVisits = history.totalVisits,
                    iitEpisodes = history.priorIitEpisodes,
                    latePickups = history.priorLatePickups,
                    hadPriorEac = eacByUuid[c.uuid]?.isNotEmpty() == true,
                )
                val pct = (eacHead.probability(feat) * 100).roundToInt()
                if (pct >= 40) {
                    val f = PatientRiskEngine.RiskFactor(
                        PatientRiskEngine.RiskDomain.VIROLOGIC,
                        "AI: high risk of EAC re-suppression failure ($pct%) — escalate adherence support",
                        (pct / 4).coerceIn(0, 25),
                    )
                    val cascadeSum = (score.factors + f).filter { it.domain in cascadeDomains }.sumOf { it.points }
                    val newScore = maxOf(score.score, cascadeSum).coerceAtMost(100)
                    val b2 = PatientRiskEngine.bandForScore(newScore)
                    score = score.copy(
                        factors = listOf(f) + score.factors,
                        score = newScore,
                        band = if (b2.ordinal < score.band.ordinal) b2 else score.band,
                    )
                }
            }

            // An actively-lapsing client gets the return-to-care message (the cascade happens when they
            // come). An up-to-date client with a cascade gap gets the targeted, more-actionable recall.
            val unsuppressed = (patient?.lastViralLoadResult ?: 0L) >= 1000L
            val eacIncomplete = eacLatest != null && eacLatest.stage != "COMPLETE" && eacLatest.sessions < 3
            val type = when {
                score.isApproachingIit -> ReminderType.APPROACHING_IIT
                daysOverdue > IIT_THRESHOLD_DAYS -> ReminderType.ESTABLISHED_IIT
                topInfant?.gapType != null -> ReminderType.EID_RECALL
                pmtct?.gapType != null -> ReminderType.PMTCT_RECALL
                unsuppressed && eacIncomplete -> ReminderType.EAC_RECALL
                else -> ReminderType.FORECAST
            }
            AssessedClient(
                score = score,
                type = type,
                target = ReminderTarget(c.displayName, patient?.phoneNumber, email = null),
                context = ReminderContext(
                    displayName = c.displayName,
                    facilityName = facilityName,
                    appointmentDate = appt,
                    daysOverdue = daysOverdue,
                    daysUntilAppointment = daysUntil ?: 0,
                ),
            )
        }

        return RiskAssessment(
            // Up-to-date clients are flagged when band is Moderate+, OR when they carry a cascade recall
            // (a PMTCT/EID/EAC gap is actionable even at a low IIT-forecast band).
            forecast = assessed.filter {
                it.score.isForecast && (it.score.band != RiskBand.LOW || it.type in cascadeReminderTypes)
            }.sortedByDescending { it.score.score },
            approaching = assessed.filter { it.score.isApproachingIit }
                .sortedByDescending { it.score.score },
            established = assessed.filter { it.score.daysOverdue > IIT_THRESHOLD_DAYS }
                .sortedByDescending { it.score.score },
            all = assessed,
        )
    }
}
