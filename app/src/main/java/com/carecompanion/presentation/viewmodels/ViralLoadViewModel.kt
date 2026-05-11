package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.SharedPreferencesHelper
import com.carecompanion.utils.ViralLoadEligibilityEngine
import com.carecompanion.utils.ViralLoadEligibilityResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class ViralLoadTab {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    PENDING_RESULT,
    OVERDUE_PENDING
}

data class ViralLoadClientItem(
    val patient: Patient,
    val eligibility: ViralLoadEligibilityResult,
    val sampleCollectionDate: Date?,  // Date sample was collected (per PEPFAR/NACA standard)
    val resultReportedDate: Date?,    // Date result was reported
    val latestViralLoadResult: String?,
    val statusLabel: String,
    val daysWithoutResult: Int? = null,  // Days since sample collection without result (for pending results)
    val flagLabel: String? = null,
)

data class ViralLoadUiState(
    val selectedTab: ViralLoadTab = ViralLoadTab.TODAY,
    val clients: List<ViralLoadClientItem> = emptyList(),
    val dueTodayCount: Int = 0,
    val dueThisWeekCount: Int = 0,
    val dueThisMonthCount: Int = 0,
    val pendingResultCount: Int = 0,
    val overduePendingCount: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class ViralLoadViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val watTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos")
    private val viralLoadHistoryCache = mutableMapOf<String, List<ViralLoadHistory>>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedTab = MutableStateFlow(ViralLoadTab.TODAY)
    val selectedTab: StateFlow<ViralLoadTab> = _selectedTab.asStateFlow()

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val groupedClientsFlow = _searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val fid = facilityId()
            val baseFlow = if (fid > 0) {
                patientRepository.observeAllActiveByFacility(fid)
            } else {
                patientRepository.observeAllActive()
            }

            baseFlow.map { patients ->
                val normalizedQuery = query.trim().lowercase()
                val dueTodayItems = mutableListOf<ViralLoadClientItem>()
                val dueThisWeekItems = mutableListOf<ViralLoadClientItem>()
                val dueThisMonthItems = mutableListOf<ViralLoadClientItem>()
                val pendingResultItems = mutableListOf<ViralLoadClientItem>()
                val overduePendingItems = mutableListOf<ViralLoadClientItem>()
                
                val todayStart = startOfDay(Date())
                val weekRange = currentWeekRange(todayStart)

                patients
                    .filter { it.isArtEnrolled() }
                    .forEach { patient ->
                        val personUuid = patient.personUuid ?: patient.uuid
                        val history = viralLoadHistoryCache[personUuid] ?: patientRepository
                            .getViralLoadHistory(personUuid)
                            .also { viralLoadHistoryCache[personUuid] = it }

                        // Get the latest sample (by sample date, not result date)
                        val latestMappedVl = history
                            .asSequence()
                            .filter { it.sampleTypeId == 5 }
                            .sortedByDescending { it.sampleDate?.time ?: Long.MIN_VALUE }
                            .firstOrNull()

                        // PEPFAR/NACA standard: Use sample collection date for eligibility, not result date
                        val sampleCollectionDate = latestMappedVl?.sampleDate
                        val resultReportedDate = latestMappedVl?.resultDate ?: latestMappedVl?.assayedDate

                        // Check if result is pending (sample collected but no result)
                        val hasResult = !latestMappedVl?.resultRaw.isNullOrBlank()
                        val daysWithoutResult = if (!hasResult && sampleCollectionDate != null) {
                            java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - sampleCollectionDate.time).toInt()
                        } else {
                            null
                        }

                        // Compute eligibility using sample collection date (per PEPFAR/NACA standard)
                        val eligibility = ViralLoadEligibilityEngine.evaluate(
                            dateOfBirth = patient.dateOfBirth,
                            artRegistrationDate = patient.dateOfRegistration,
                            dateSampleCollected = sampleCollectionDate,  // Use sample date for eligibility
                            dateResultReported = resultReportedDate,
                        )
                        if (eligibility == null) return@forEach

                        // Determine status label based on result availability
                        val statusLabel = when {
                            !hasResult && daysWithoutResult != null && daysWithoutResult > 30 -> "Overdue pending result"
                            !hasResult && daysWithoutResult != null -> "Result pending"
                            hasResult -> {
                                val numeric = latestMappedVl?.resultNumeric?.toDouble()
                                when {
                                    numeric != null && numeric <= 20 -> "Undetected"
                                    numeric != null && numeric < 1000 -> "Suppressed"
                                    numeric != null -> "Unsuppressed"
                                    else -> "Unknown"
                                }
                            }
                            else -> "No sample collected"
                        }

                        val item = ViralLoadClientItem(
                            patient = patient,
                            eligibility = eligibility,
                            sampleCollectionDate = sampleCollectionDate,
                            resultReportedDate = resultReportedDate,
                            latestViralLoadResult = latestMappedVl?.resultRaw,
                            statusLabel = statusLabel,
                            daysWithoutResult = daysWithoutResult,
                            flagLabel = if (!hasResult && daysWithoutResult != null && daysWithoutResult > 30) 
                                "Overdue pending result: ${daysWithoutResult} days without result" else null,
                        )

                        // Group by status: categorize into appropriate tabs
                        if (!hasResult && sampleCollectionDate != null) {
                            // Sample collected but result pending
                            if (daysWithoutResult != null && daysWithoutResult > 30) {
                                // Overdue: more than 30 days without result
                                overduePendingItems.add(item)
                            } else {
                                // Recently collected (pending result <30 days)
                                pendingResultItems.add(item)
                            }
                        } else if (hasResult) {
                            // Result available: categorize by due date
                            val dueDate = startOfDay(eligibility.dueDate)

                            when {
                                isSameDay(dueDate, todayStart) -> dueTodayItems.add(item)
                                dueDate.after(todayStart) && !dueDate.after(weekRange.second) -> dueThisWeekItems.add(item)
                                dueDate.after(todayStart) && isSameMonth(dueDate, todayStart) -> dueThisMonthItems.add(item)
                                // If result is older and next due is past: goes into Due Today category if eligible
                                dueDate.before(todayStart) -> dueTodayItems.add(item)
                            }
                        } else {
                            // No sample collected yet
                            val dueDate = startOfDay(eligibility.dueDate)
                            when {
                                isSameDay(dueDate, todayStart) -> dueTodayItems.add(item)
                                dueDate.after(todayStart) && !dueDate.after(weekRange.second) -> dueThisWeekItems.add(item)
                                dueDate.after(todayStart) && isSameMonth(dueDate, todayStart) -> dueThisMonthItems.add(item)
                                dueDate.before(todayStart) -> dueTodayItems.add(item)  // Overdue
                            }
                        }
                    }

                fun List<ViralLoadClientItem>.matchesQuery(): List<ViralLoadClientItem> {
                    if (normalizedQuery.isBlank()) return this
                    return filter { item ->
                        val name = item.patient.fullName.orEmpty().lowercase()
                        val first = item.patient.firstName.orEmpty().lowercase()
                        val last = item.patient.surname.orEmpty().lowercase()
                        val hospital = item.patient.hospitalNumber.lowercase()
                        name.contains(normalizedQuery)
                            || first.contains(normalizedQuery)
                            || last.contains(normalizedQuery)
                            || hospital.contains(normalizedQuery)
                    }
                }

                mapOf(
                    ViralLoadTab.TODAY to dueTodayItems
                        .matchesQuery()
                        .sortedWith(compareBy<ViralLoadClientItem> { it.eligibility.dueDate.time }.thenBy { it.patient.fullName ?: "" }),
                    ViralLoadTab.THIS_WEEK to dueThisWeekItems
                        .matchesQuery()
                        .sortedWith(compareBy<ViralLoadClientItem> { it.eligibility.dueDate.time }.thenBy { it.patient.fullName ?: "" }),
                    ViralLoadTab.THIS_MONTH to dueThisMonthItems
                        .matchesQuery()
                        .sortedWith(compareBy<ViralLoadClientItem> { it.eligibility.dueDate.time }.thenBy { it.patient.fullName ?: "" }),
                    ViralLoadTab.PENDING_RESULT to pendingResultItems
                        .matchesQuery()
                        .sortedWith(compareByDescending<ViralLoadClientItem> { it.daysWithoutResult ?: 0 }.thenBy { it.patient.fullName ?: "" }),
                    ViralLoadTab.OVERDUE_PENDING to overduePendingItems
                        .matchesQuery()
                        .sortedWith(compareByDescending<ViralLoadClientItem> { it.daysWithoutResult ?: 0 }.thenBy { it.patient.fullName ?: "" }),
                )
            }
        }

    val uiState: StateFlow<ViralLoadUiState> = combine(groupedClientsFlow, _selectedTab, _searchQuery) { groups, selectedTab, query ->
        ViralLoadUiState(
            selectedTab = selectedTab,
            clients = groups[selectedTab].orEmpty(),
            dueTodayCount = groups[ViralLoadTab.TODAY].orEmpty().size,
            dueThisWeekCount = groups[ViralLoadTab.THIS_WEEK].orEmpty().size,
            dueThisMonthCount = groups[ViralLoadTab.THIS_MONTH].orEmpty().size,
            pendingResultCount = groups[ViralLoadTab.PENDING_RESULT].orEmpty().size,
            overduePendingCount = groups[ViralLoadTab.OVERDUE_PENDING].orEmpty().size,
            isLoading = false,
            searchQuery = query,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ViralLoadUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun selectTab(tab: ViralLoadTab) {
        _selectedTab.value = tab
    }

    private fun startOfDay(date: Date): Date {
        val cal = Calendar.getInstance(watTimeZone)
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun endOfDay(date: Date): Date {
        val cal = Calendar.getInstance(watTimeZone)
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }

    private fun currentWeekRange(reference: Date): Pair<Date, Date> {
        val cal = Calendar.getInstance(watTimeZone)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.time = reference
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val delta = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        cal.add(Calendar.DAY_OF_MONTH, delta)
        val weekStart = startOfDay(cal.time)
        cal.time = weekStart
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val weekEnd = endOfDay(cal.time)
        return weekStart to weekEnd
    }

    private fun isSameDay(left: Date, right: Date): Boolean {
        val l = Calendar.getInstance(watTimeZone).apply { time = left }
        val r = Calendar.getInstance(watTimeZone).apply { time = right }
        return l.get(Calendar.YEAR) == r.get(Calendar.YEAR)
            && l.get(Calendar.DAY_OF_YEAR) == r.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameMonth(left: Date, right: Date): Boolean {
        val l = Calendar.getInstance(watTimeZone).apply { time = left }
        val r = Calendar.getInstance(watTimeZone).apply { time = right }
        return l.get(Calendar.YEAR) == r.get(Calendar.YEAR)
            && l.get(Calendar.MONTH) == r.get(Calendar.MONTH)
    }
}
