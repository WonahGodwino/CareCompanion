package com.carecompanion.presentation.viewmodels
import androidx.lifecycle.ViewModel
import com.carecompanion.data.database.entities.Patient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
@HiltViewModel
class SharedViewModel @Inject constructor() : ViewModel() {
    private val _selectedPatient = MutableStateFlow<Patient?>(null)
    val selectedPatient: StateFlow<Patient?> = _selectedPatient.asStateFlow()
    fun setSelectedPatient(patient: Patient) { _selectedPatient.value = patient }
    fun clearSelectedPatient() { _selectedPatient.value = null }
}