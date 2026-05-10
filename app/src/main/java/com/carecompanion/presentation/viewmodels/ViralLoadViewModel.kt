package com.carecompanion.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carecompanion.data.database.entities.Patient
import com.carecompanion.data.database.entities.ViralLoadHistory
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.SharedPreferencesHelper
import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.ViralLoadEligibilityEngine
import com.carecompanion.utils.ViralLoadEligibilityResult
import com.carecompanion.utils.ViralLoadDueType
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
import javax.inject.Inject

data class ViralLoadClientItem(
    val patient: Patient,
    val eligibility: ViralLoadEligibilityResult,
    val latestViralLoadDate: java.util.Date?,
    val latestViralLoadResult: String?,
    val statusLabel: String,
    val flagLabel: String? = null,
)

data class ViralLoadUiState(
    val clients: List<ViralLoadClientItem> = emptyList(),
    val baselineDueCount: Int = 0,
    val routineDueCount: Int = 0,
    val totalDueCount: Int = 0,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class ViralLoadViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val viralLoadHistoryCache = mutableMapOf<String, List<ViralLoadHistory>>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private fun facilityId() = SharedPreferencesHelper.getActiveFacilityId(context)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val dueClientsFlow = _searchQuery
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
                val dueItems = mutableListOf<ViralLoadClientItem>()

                patients
                    .filter { it.isArtEnrolled() }
                    .forEach { patient ->
                        val personUuid = patient.personUuid ?: patient.uuid
                        val history = viralLoadHistoryCache[personUuid] ?: patientRepository
                            .getViralLoadHistory(personUuid)
                            .also { viralLoadHistoryCache[personUuid] = it }

                        val latestMappedVl = history
                            .asSequence()
                            .filter { it.sampleTypeId == 5 }
                            .sortedByDescending {
                                it.resultDate?.time
                                    ?: it.assayedDate?.time
                                    ?: it.sampleDate?.time
                                    ?: Long.MIN_VALUE
                            }
                            .firstOrNull()

                        val latestVlDate = latestMappedVl?.let {
                            it.resultDate ?: it.assayedDate ?: it.sampleDate
                        }

                        val pending = latestMappedVl?.resultRaw.isNullOrBlank()
                        val sampleDate = latestMappedVl?.sampleDate
                        val pendingAgeDays = sampleDate?.let {
                            java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it.time).toInt()
                        } ?: 0

                        if (pending && pendingAgeDays in 0..30) {
                            return@forEach
                        }

                        val eligibility = ViralLoadEligibilityEngine.evaluate(
                            dateOfBirth = patient.dateOfBirth,
                            artRegistrationDate = patient.dateOfRegistration,
                            latestViralLoadDate = latestVlDate,
                        )
                        if (eligibility != null && eligibility.isEligibleToday) {
                            dueItems.add(
                                ViralLoadClientItem(
                                    patient = patient,
                                    eligibility = eligibility,
                                    latestViralLoadDate = latestVlDate,
                                    latestViralLoadResult = latestMappedVl?.resultRaw,
                                    statusLabel = when {
                                        pending && pendingAgeDays > 30 -> "Overdue sample with no Result"
                                        pending -> "Result Pending"
                                        else -> {
                                            val numeric = latestMappedVl?.resultNumeric?.toDouble()
                                            when {
                                                numeric != null && numeric <= 20 -> "Undetected"
                                                numeric != null && numeric < 1000 -> "Suppressed"
                                                numeric != null -> "Unsuppressed"
                                                else -> "Unknown"
                                            }
                                        }
                                    },
                                    flagLabel = if (pending && pendingAgeDays > 30) "Overdue sample with no Result" else null,
                                )
                            )
                        }
                    }

                dueItems
                    .filter { item ->
                        if (normalizedQuery.isBlank()) return@filter true
                        val name = item.patient.fullName.orEmpty().lowercase()
                        val first = item.patient.firstName.orEmpty().lowercase()
                        val last = item.patient.surname.orEmpty().lowercase()
                        val hospital = item.patient.hospitalNumber.lowercase()
                        name.contains(normalizedQuery)
                            || first.contains(normalizedQuery)
                            || last.contains(normalizedQuery)
                            || hospital.contains(normalizedQuery)
                    }
                    .sortedWith(
                        compareByDescending<ViralLoadClientItem> { it.eligibility.daysOverdue }
                            .thenBy { it.patient.fullName ?: "" }
                    )
            }
        }

    val uiState: StateFlow<ViralLoadUiState> = combine(dueClientsFlow, _searchQuery) { clients, query ->
        ViralLoadUiState(
            clients = clients,
            baselineDueCount = clients.count { it.eligibility.dueType == ViralLoadDueType.BASELINE },
            routineDueCount = clients.count { it.eligibility.dueType == ViralLoadDueType.ROUTINE },
            totalDueCount = clients.size,
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
}
