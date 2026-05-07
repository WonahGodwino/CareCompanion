# Service Eligibility Integration - Quick Checklist

## ✅ Implementation Status: COMPLETE

### WINCO Backend (C:\Winco)
- [x] **services/service_eligibility.py** - ServiceEligibilityEngine class created (500+ lines)
  - [x] 5 service calculation methods (ART_REFILL, MISSED_APPOINTMENT, VIRAL_LOAD, TPT, TB_AHD)
  - [x] Configurable thresholds as class constants
  - [x] Python syntax validated: `python -m py_compile services/service_eligibility.py` ✅
  
- [x] **routes/art_api_routes.py** - get_art_client endpoint updated
  - [x] Import ServiceEligibilityEngine
  - [x] Instantiate engine with as_of_date
  - [x] Gather patient data from EMR latest records
  - [x] Call calculate_all_service_eligibilities()
  - [x] Include service_eligibility in JSON response

### Care Companion Mobile (C:\CareCompanion)
- [x] **WincoModels.kt** - Data classes for API response
  - [x] WincoServiceEligibility data class (service, eligible, reason, urgency, nextAction, detail fields)
  - [x] WincoServiceEligibilitySummary data class (asOfDate, services map, eligibleServices list, summary)
  - [x] WincoClientDetail updated with serviceEligibility field
  - [x] All @SerializedName annotations added for JSON mapping

- [x] **PatientProfileViewModel.kt** - ViewModel updated
  - [x] ServiceEligibilityUI data class created
  - [x] PatientProfileUiState includes serviceEligibility, eligibleServices, eligibleCount
  - [x] Constructor includes WincoApiService dependency
  - [x] loadPatient() fetches clientDetail via API
  - [x] Service eligibility data mapped to UIState
  - [x] API error handling (log and continue)

- [x] **PatientScreen.kt** - UI updated with real data
  - [x] Added import for Patient entity class
  - [x] Added import for ServiceEligibilityUI
  - [x] Replaced hardcoded ServiceEligibilitySection with data-driven version
  - [x] Removed old ServiceEligibilityItem data class
  - [x] New ServiceEligibilitySection accepts serviceEligibility map, eligibleCount, totalCount
  - [x] Service display order: ART_REFILL, MISSED_APPOINTMENT, VIRAL_LOAD, TPT, TB_AHD
  - [x] Color-coded urgency: critical=red, high=orange, due=gold, routine=green
  - [x] UI shows: service name, reason, urgency pill, eligible/not eligible badge
  - [x] Eligible services count (X/5 Eligible) in header

### Compilation Verification
- [x] Python: `python -m py_compile services/service_eligibility.py` → No errors ✅
- [x] Kotlin: `./gradlew compileDebugKotlin --no-daemon` → BUILD SUCCESSFUL ✅
  - Only deprecation warnings (Icons.AutoMirrored.ArrowBack) - not critical
  - No compilation errors

### Testing Checklist
- [ ] Deploy WINCO backend (services/service_eligibility.py + routes/art_api_routes.py updates)
- [ ] Restart WINCO Flask app: `python app.py`
- [ ] Verify API endpoint: `GET /api/art/clients/{person_uuid}` returns service_eligibility JSON
- [ ] Build Care Companion: `./gradlew assembleDebug`
- [ ] Deploy to emulator/device
- [ ] Load patient profile on mobile
- [ ] Verify ServiceEligibilitySection displays:
  - [ ] Real data from API (not hardcoded)
  - [ ] All 5 services with correct eligibility status
  - [ ] Color-coded urgency levels
  - [ ] Eligible services count (X/5)

---

## Files Modified Summary

### WINCO Backend
| File | Changes | Status |
|------|---------|--------|
| services/service_eligibility.py | New file - 500+ lines | ✅ Created |
| routes/art_api_routes.py | Import + engine instantiation + response field | ✅ Modified |
| Documentation | SERVICE_ELIGIBILITY_IMPLEMENTATION.md | ✅ Created |
| Documentation | WINCO_CARE_COMPANION_SERVICE_INTEGRATION.md | ✅ Created |

### Care Companion Mobile
| File | Changes | Status |
|------|---------|--------|
| data/network/models/WincoModels.kt | Added 2 data classes + WincoClientDetail update | ✅ Modified |
| presentation/viewmodels/PatientProfileViewModel.kt | Added UIState fields + API integration | ✅ Modified |
| presentation/ui/patient/PatientScreen.kt | Replaced ServiceEligibilitySection with data-driven version | ✅ Modified |

---

## API Response Example

```json
{
  "service_eligibility": {
    "as_of_date": "2024-12-11",
    "services": {
      "ART_REFILL": {
        "service": "ART_REFILL",
        "eligible": true,
        "reason": "Medication expires in 5 days (2024-12-16)",
        "urgency": "routine",
        "next_action": "Schedule refill appointment",
        "medication_expiry_date": "2024-12-16",
        "days_until_expiry": 5
      },
      "MISSED_APPOINTMENT": {
        "service": "MISSED_APPOINTMENT",
        "eligible": true,
        "reason": "Clinical appointment overdue by 10 days",
        "urgency": "high",
        "next_action": "Contact patient to reschedule",
        "days_overdue": 10
      },
      "VIRAL_LOAD": {
        "service": "VIRAL_LOAD",
        "eligible": false,
        "reason": "Last VL done 8 months ago, routine not due until 12 months",
        "urgency": null,
        "next_action": null,
        "vl_type": "routine",
        "months_since_last_vl": 8
      },
      "TPT": {
        "service": "TPT",
        "eligible": true,
        "reason": "TB-negative, CD4 count 150 (< 350 threshold), not on TPT",
        "urgency": "critical",
        "next_action": "Start TPT immediately",
        "cd4_count": 150,
        "tb_status": "N",
        "priority": "HIGH"
      },
      "TB_AHD": {
        "service": "TB_AHD",
        "eligible": false,
        "reason": "TB screening done 1 month ago, due after 3 months",
        "urgency": null,
        "next_action": null,
        "tb_status": "N"
      }
    },
    "eligible_services": ["ART_REFILL", "MISSED_APPOINTMENT", "TPT"],
    "summary": {
      "total_services": 5,
      "eligible_count": 3
    }
  }
}
```

---

## Color Coding Reference

| Urgency | Color | Use Case |
|---------|-------|----------|
| critical | Red (#FF0000) | Immediate action needed (CD4 < 350 on TPT, missed >28 days) |
| high | Orange (#FFA500) | Soon (TPT eligible, >14 days overdue) |
| due | Gold (#FFD700) | Within week (appointment due soon, VL due soon) |
| routine | Green (#4CAF50) | Normal (ART refill routine, TB screening routine) |
| Not eligible | Gray | Service not applicable |

---

## Troubleshooting

### Service eligibility not displaying
```
1. Check WINCO API response includes service_eligibility field
   curl -H "Authorization: Bearer TOKEN" https://winco-server/api/art/clients/{uuid}
   
2. Check PatientProfileViewModel logs for API errors
   adb logcat | grep PatientProfileViewModel
   
3. Verify WincoModels.kt has @SerializedName annotations
```

### Incorrect eligibility calculations
```
1. Check ServiceEligibilityEngine logic in services/service_eligibility.py
2. Verify EMR source data (pharmacy, clinical, enrollment records exist)
3. Check thresholds are set correctly (MISSED_APPOINTMENT_THRESHOLD_DAYS, etc.)
```

### UI not updating after API call
```
1. Verify uiState.serviceEligibility is populated in ViewModel
2. Check ServiceEligibilitySection receives non-empty map
3. Verify Kotlin code compiles: ./gradlew compileDebugKotlin
```

---

## Next Steps for Production

1. **Code Review:** Review all changes with team
2. **Staging Test:** Deploy to staging environment
3. **Integration Test:** Verify with real patient data
4. **UAT:** Health workers test on mobile app
5. **Documentation:** Update training materials
6. **Production Deploy:** Roll out to all facilities

---

**Status:** ✅ **READY FOR TESTING**
**Last Updated:** December 2024
