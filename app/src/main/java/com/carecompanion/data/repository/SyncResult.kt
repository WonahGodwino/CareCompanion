package com.carecompanion.data.repository

sealed class SyncResult {
	data class SyncAudit(
		val pagesRead: Int,
		val biometricCandidates: Int,
		val biometricsSkipped: Int,
		val biometricsFailed: Int
	)

	data class Success(
		val patientsAdded: Int,
		val biometricsAdded: Int,
		val viralLoadHistoryAdded: Int,
		val pharmacyHistoryAdded: Int,
		val audit: SyncAudit = SyncAudit(0, 0, 0, 0)
	) : SyncResult()
	data class Error(val message: String) : SyncResult()
	object NoNetwork : SyncResult()
	object NotConfigured : SyncResult()
}

