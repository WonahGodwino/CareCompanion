package com.carecompanion.data.risk

import android.content.Context
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.GeoUtils
import com.carecompanion.utils.PatientRiskEngine
import com.carecompanion.utils.PatientRiskEngine.AdherenceHistory
import com.carecompanion.utils.PatientRiskEngine.IIT_THRESHOLD_DAYS
import com.carecompanion.utils.PatientRiskEngine.PatientSignals
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ClassMetrics(
    val n: Int,
    val positives: Int,
    val sensitivity: Double,
    val specificity: Double,
    val ppv: Double,
    val accuracy: Double,
    val auc: Double,
)

data class FeatureWeight(val name: String, val weight: Double)

data class BacktestResult(
    val sampleCount: Int,
    val patientCount: Int,
    val iitRate: Double,
    val folds: Int,
    val heuristic: ClassMetrics,     // cross-validated
    val learned: ClassMetrics,       // cross-validated (out-of-fold)
    val learnedWeights: List<FeatureWeight>,
    val model: LearnedRiskModel,     // trained on ALL data, ready to adopt
    val canAdopt: Boolean,           // guardrail: learned beats heuristic AND is useful
)

/**
 * Validates the AI against the clinic's OWN history with **k-fold cross-validation** (no
 * leakage: folds are split by patient), and produces a model trained on all data that the
 * user may adopt — but only if it clears the guardrail.
 *
 * Method: for each past appointment we know whether the client returned within the 28-day
 * IIT threshold (label). We reconstruct the features knowable *before* that appointment via
 * [RiskFeatures] — the same builder live scoring uses — predict, and compare to truth.
 */
@Singleton
class RiskBacktestService @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context,
) {
    private val kFolds = 5
    private val usefulAucFloor = 0.60

    private class Sample(
        val uuid: String,
        val features: DoubleArray,
        val label: Int,
        // retained so the heuristic engine can be scored on the same point
        val dob: Date?, val episodes: Int, val late: Int, val gaps: Int, val refill: Int,
        val distanceKm: Double?, val hasPhone: Boolean, val vlValue: Long?,
    )

    suspend fun run(): BacktestResult {
        val fid = SharedPreferencesHelper.getActiveFacilityId(context)
        val patients = if (fid > 0) patientRepository.getAllActiveByFacility(fid)
            else patientRepository.getAllActivePatients()
        val patientByUuid = patients.associateBy { it.uuid }
        val pharmByUuid = patientRepository.getArtPharmacyForPatients(patients.map { it.uuid })
            .groupBy { it.personUuid }
        val fLat = SharedPreferencesHelper.getFacilityLatitude(fid)
        val fLng = SharedPreferencesHelper.getFacilityLongitude(fid)
        // VL history per patient (ascending date,value) — for "latest VL before appointment".
        val vlByUuid = patientRepository.getViralLoadHistoryForPatients(patients.map { it.uuid })
            .groupBy { it.personUuid }
            .mapValues { (_, list) ->
                list.mapNotNull { vl ->
                    val d = vl.resultDate ?: vl.assayedDate ?: vl.sampleDate
                    val v = vl.resultNumeric
                    if (d != null && v != null) d to v else null
                }.sortedBy { it.first }
            }

        val samples = ArrayList<Sample>()
        for ((uuid, recordsRaw) in pharmByUuid) {
            val records = recordsRaw.sortedBy { it.visitDate }
            if (records.size < 3) continue
            val patient = patientByUuid[uuid]
            val dob = patient?.dateOfBirth
            val distanceKm = if (fLat != null && fLng != null)
                GeoUtils.haversineKm(GeoUtils.parseCoord(patient?.latitude), GeoUtils.parseCoord(patient?.longitude), fLat, fLng)
            else null
            val hasPhone = !patient?.phoneNumber.isNullOrBlank()
            val vlList = vlByUuid[uuid]

            for (i in 1..records.size - 2) {
                val appt = records[i].nextAppointment ?: continue
                val daysLate = TimeUnit.MILLISECONDS.toDays(records[i + 1].visitDate.time - appt.time)
                val label = if (daysLate > IIT_THRESHOLD_DAYS) 1 else 0

                var episodes = 0; var late = 0
                for (j in 0 until i) {
                    val sched = records[j].nextAppointment ?: continue
                    val back = TimeUnit.MILLISECONDS.toDays(records[j + 1].visitDate.time - sched.time)
                    if (back > 0) late++
                    if (back > IIT_THRESHOLD_DAYS) episodes++
                }
                val age = DateUtils.calculateAge(dob)
                val refill = records[i].refillPeriod ?: 0
                val vlValue = latestVlBefore(vlList, records[i].visitDate)
                val features = RiskFeatures.vector(episodes, late, gaps = i, ageYears = age, refillDays = refill,
                    distanceKm = distanceKm, hasPhone = hasPhone, vlValue = vlValue)
                samples += Sample(uuid, features, label, dob, episodes, late, i, refill, distanceKm, hasPhone, vlValue)
            }
        }

        require(samples.size >= 20) {
            "Not enough history yet to validate (need ≥20 past appointments, have ${samples.size})."
        }

        val labels = samples.map { it.label }.toIntArray()

        // ── Heuristic predictions (no training; evaluate every sample once) ──
        val heuristicProbs = samples.map { heuristicProbability(it) }

        // ── k-fold cross-validated learned predictions (out-of-fold) ──
        val oof = DoubleArray(samples.size) { Double.NaN }
        for (f in 0 until kFolds) {
            val trainIdx = samples.indices.filter { abs(samples[it].uuid.hashCode()) % kFolds != f }
            val testIdx = samples.indices.filter { abs(samples[it].uuid.hashCode()) % kFolds == f }
            if (trainIdx.isEmpty() || testIdx.isEmpty()) continue
            val fold = LogisticRegression()
            fold.fit(trainIdx.map { samples[it].features }, trainIdx.map { samples[it].label }.toIntArray())
            for (i in testIdx) oof[i] = fold.probability(samples[i].features)
        }
        // Any sample whose fold was degenerate falls back to base rate (neutral).
        val baseRate = labels.count { it == 1 }.toDouble() / labels.size
        val learnedProbs = oof.map { if (it.isNaN()) baseRate else it }

        val heuristic = metrics(heuristicProbs, labels, threshold = 0.30)   // MODERATE+ = flagged
        val learned = metrics(learnedProbs, labels, threshold = 0.50)

        // ── Final model trained on ALL data (this is what gets adopted) ──
        val finalModel = LogisticRegression()
        finalModel.fit(samples.map { it.features }, labels)
        val model = LearnedRiskModel(
            schemaVersion = RiskFeatures.SCHEMA_VERSION,
            featureNames = RiskFeatures.NAMES,
            weights = finalModel.weights,
            bias = finalModel.bias,
            mean = finalModel.mean,
            std = finalModel.std,
            nSamples = samples.size,
            auc = learned.auc,
            heuristicAuc = heuristic.auc,
            trainedAt = System.currentTimeMillis(),
        )

        return BacktestResult(
            sampleCount = samples.size,
            patientCount = pharmByUuid.size,
            iitRate = baseRate,
            folds = kFolds,
            heuristic = heuristic,
            learned = learned,
            learnedWeights = RiskFeatures.NAMES.mapIndexed { idx, name -> FeatureWeight(name, finalModel.weights[idx]) }
                .sortedByDescending { abs(it.weight) },
            model = model,
            canAdopt = learned.auc >= heuristic.auc && learned.auc >= usefulAucFloor,
        )
    }

    /** Most recent VL value strictly before [ref] (vlList ascending by date), or null. */
    private fun latestVlBefore(vlList: List<Pair<Date, Long>>?, ref: Date): Long? {
        if (vlList == null) return null
        var latest: Long? = null
        for ((d, v) in vlList) {
            if (d.before(ref)) latest = v else break
        }
        return latest
    }

    private fun heuristicProbability(s: Sample): Double {
        val score = PatientRiskEngine.score(
            PatientSignals(
                uuid = s.uuid, displayName = "", hospitalNumber = "", initial = '?',
                dateOfBirth = s.dob,
                daysOverdue = 0, daysUntilAppointment = 1,
                refillPeriodDays = s.refill,
                history = AdherenceHistory(s.gaps, s.late, s.episodes, 0),
                distanceToFacilityKm = s.distanceKm,
                hasPhoneContact = s.hasPhone,
                lastViralLoadResult = s.vlValue,   // fair: heuristic sees the same VL as the model
            )
        )
        return score.score / 100.0
    }

    private fun metrics(probs: List<Double>, labels: IntArray, threshold: Double): ClassMetrics {
        var tp = 0; var tn = 0; var fp = 0; var fn = 0
        for (i in probs.indices) {
            val pred = if (probs[i] >= threshold) 1 else 0
            when {
                pred == 1 && labels[i] == 1 -> tp++
                pred == 1 && labels[i] == 0 -> fp++
                pred == 0 && labels[i] == 0 -> tn++
                else -> fn++
            }
        }
        val pos = tp + fn
        val neg = tn + fp
        return ClassMetrics(
            n = labels.size,
            positives = pos,
            sensitivity = if (pos > 0) tp.toDouble() / pos else 0.0,
            specificity = if (neg > 0) tn.toDouble() / neg else 0.0,
            ppv = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0,
            accuracy = if (labels.isNotEmpty()) (tp + tn).toDouble() / labels.size else 0.0,
            auc = auc(probs, labels),
        )
    }

    private fun auc(probs: List<Double>, labels: IntArray): Double {
        val pos = probs.indices.filter { labels[it] == 1 }.map { probs[it] }
        val neg = probs.indices.filter { labels[it] == 0 }.map { probs[it] }
        if (pos.isEmpty() || neg.isEmpty()) return 0.5
        var wins = 0.0
        for (p in pos) for (n in neg) wins += when {
            p > n -> 1.0
            p == n -> 0.5
            else -> 0.0
        }
        return wins / (pos.size * neg.size)
    }
}
