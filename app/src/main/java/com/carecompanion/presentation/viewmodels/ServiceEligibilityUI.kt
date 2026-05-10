package com.carecompanion.presentation.viewmodels

data class ServiceEligibilityUI(
    val service: String,
    val eligible: Boolean,
    val reason: String? = null,
    val urgency: String? = null,
    val nextAction: String? = null,
    val careCategory: String? = null,
    val eligibilityGroup: String? = null,
    val details: Map<String, Any> = emptyMap(),
)