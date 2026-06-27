package com.carecompanion.utils

/**
 * Maps LAMIS II regimen IDs to human-readable names.
 *
 * IDs are aligned with the Nigerian NACA/NPHCDA LAMIS II regimen list.
 * Verify the exact IDs against the facility's LAMIS configuration if
 * discrepancies are observed — LAMIS customisations can shift numeric IDs.
 */
object RegimenLookup {

    data class Regimen(val shortName: String, val fullName: String, val line: String)

    private val map: Map<Long, Regimen> = mapOf(
        // ── First-line (Adults) ──────────────────────────────────────────────
        1L  to Regimen("AZT/3TC/NVP",       "Zidovudine + Lamivudine + Nevirapine",           "1st Line"),
        2L  to Regimen("AZT/3TC/EFV",       "Zidovudine + Lamivudine + Efavirenz 600",        "1st Line"),
        3L  to Regimen("TDF/3TC/EFV 600",   "Tenofovir + Lamivudine + Efavirenz 600",         "1st Line"),
        4L  to Regimen("TDF/3TC/NVP",       "Tenofovir + Lamivudine + Nevirapine",            "1st Line"),
        5L  to Regimen("TDF/FTC/EFV",       "Tenofovir + Emtricitabine + Efavirenz",          "1st Line"),
        6L  to Regimen("TDF/3TC/EFV 400",   "Tenofovir + Lamivudine + Efavirenz 400 (TLE400)","1st Line"),
        // ── First-line — Dolutegravir (preferred, Nigeria 2019 guidelines) ──
        67L to Regimen("TLD",               "Tenofovir + Lamivudine + Dolutegravir (TLD)",    "1st Line"),
        68L to Regimen("AZT/3TC/DTG",       "Zidovudine + Lamivudine + Dolutegravir",         "1st Line"),
        69L to Regimen("ABC/3TC/DTG",       "Abacavir + Lamivudine + Dolutegravir",           "1st Line"),
        // ── Second-line (Adults) ─────────────────────────────────────────────
        10L to Regimen("AZT/3TC/LPV/r",     "Zidovudine + Lamivudine + Lopinavir/ritonavir",  "2nd Line"),
        11L to Regimen("TDF/3TC/LPV/r",     "Tenofovir + Lamivudine + Lopinavir/ritonavir",   "2nd Line"),
        12L to Regimen("AZT/3TC/ATV/r",     "Zidovudine + Lamivudine + Atazanavir/ritonavir", "2nd Line"),
        13L to Regimen("TDF/3TC/ATV/r",     "Tenofovir + Lamivudine + Atazanavir/ritonavir",  "2nd Line"),
        14L to Regimen("ABC/3TC/LPV/r",     "Abacavir + Lamivudine + Lopinavir/ritonavir",    "2nd Line"),
        // ── Third-line ────────────────────────────────────────────────────────
        20L to Regimen("DRV/r + DTG",       "Darunavir/ritonavir + Dolutegravir ± NRTI",      "3rd Line"),
        // ── Paediatric ───────────────────────────────────────────────────────
        30L to Regimen("ABC/3TC/NVP",       "Abacavir + Lamivudine + Nevirapine (Paed)",      "Paediatric"),
        31L to Regimen("ABC/3TC/LPV/r",     "Abacavir + Lamivudine + Lopinavir/ritonavir (Paed)", "Paediatric"),
        32L to Regimen("ABC/3TC/EFV",       "Abacavir + Lamivudine + Efavirenz (Paed)",       "Paediatric"),
        33L to Regimen("AZT/3TC/ABC",       "Zidovudine + Lamivudine + Abacavir (Paed)",      "Paediatric"),
        34L to Regimen("ABC/3TC/DTG",       "Abacavir + Lamivudine + Dolutegravir (Paed)",    "Paediatric"),
    )

    fun get(regimenId: Long?): Regimen? = regimenId?.let { map[it] }

    fun getShortName(regimenId: Long?): String? = get(regimenId)?.shortName

    fun getFullName(regimenId: Long?): String? = get(regimenId)?.fullName

    fun getLine(regimenId: Long?): String? = get(regimenId)?.line

    fun getDisplayName(regimenId: Long?): String =
        regimenId?.let { get(it)?.shortName ?: "Regimen ID: $it" } ?: "Unknown Regimen"
}
