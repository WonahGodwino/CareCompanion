# CareCompanion Acceptance Notes

## Purpose
This document outlines how CareCompanion should be presented and evolved for strong NGO acceptance in HIV programme delivery settings.

## Current Strengths
- Offline-capable Room database
- Facility-scoped sync from WINCO
- Active + IIT mobile client pull
- Biometric template retrieval and local storage
- IIT services workflow with period breakdown
- Modern Android stack suitable for maintainable field deployment

## What Must Stay Stable
- Paging-based sync
- Local biometric matching logic
- Facility scoping
- Existing active patient and IIT follow-up screens
- Low-friction settings and re-authentication flow

## Product Advantages To Emphasize
1. Field-first design
- Works after sync without constant internet
- Supports biometric-assisted identification
- Focuses on recall and treatment continuity workflows

2. Programme relevance
- Pulls ACTIVE_TX_CURR and IIT cohorts from WINCO
- IIT screen supports operational breakdown and reporting-style summaries
- Facility-aware usage for implementing partners

3. Safer change strategy
- New capabilities should preserve current local workflows
- Summary/reporting enhancements should not break active field use

## Recommended Next Mobile Enhancements
1. Home dashboard cards
- Active clients
- IIT today/this week
- Last sync timestamp
- Active biometric coverage

2. Supervisor mode filters
- Exclusive vs cumulative summary mode on more services screens
- Period picker on mobile reports

3. Adoption-ready polish
- First-run orientation for facility staff
- In-app KPI definition help
- Better empty states and sync troubleshooting messages

4. Device support readiness
- Scanner compatibility matrix in settings
- Offline health check
- Storage/sync diagnostics for support teams

## Positioning Message
CareCompanion should be presented as:

"A field-ready HIV care operations companion built for Nigerian programme realities, combining offline continuity, biometric workflows, and fast facility-level follow-up."
