# CareCompanion — AI Patient Monitoring & Federated Learning
### Design & Implementation Reference (Source of Truth)

> **Status:** Living document. Update it as work lands. This is the authoritative reference
> for the AI monitoring module and the federated-learning ("shared knowledge") programme
> until every goal below is delivered. Last updated: 2026-06-25.

---

## 1. Vision & Goals

Build an AI-powered clinical assistant for HIV/ART care that:

1. **Predicts treatment interruption (IIT) before it happens** — including *before a client even
   misses an appointment* — and drives concrete, AI-controlled outreach.
2. **Stays private and offline-first** — patient PHI never leaves the device/facility (NACA/PEPFAR
   requirement and the project's core principle).
3. **Closes the loop** — measures whether the AI's actions (reminders) actually change behaviour
   (attendance), and uses that to improve.
4. **Shares learned intelligence, not data** — so a small facility (<100 patients) reaches the same
   AI accuracy as a large facility (5,000+ patients), via **federated learning** with a central
   knowledge server. No patient record is ever centralised.

**North-star:** a system whose architecture, explainability, closed-loop action, and federated
intelligence-sharing exceed any HIV care assistant currently in field use — *and can prove it with
local validation evidence.*

---

## 2. Core Architectural Principles (do not violate)

- **P1 — PHI never leaves the facility.** Only model parameters (weights) and de-identified metrics
  are shared upward. Federated, never data-pooling.
- **P2 — Explainable by default.** Every risk score is a sum of named, weighted factors a clinician
  can audit. The model that replaces the heuristic must remain interpretable (logistic regression
  first; tree ensembles only with explanation tooling).
- **P3 — Swappable scoring.** The `PatientRiskScore` contract is stable; heuristic ↔ learned model
  is a behind-the-interface swap with no UI change.
- **P4 — Graceful degradation.** Missing data (EAC, TB, distance, model-not-yet-synced) must never
  break scoring — absent signals simply contribute nothing; fall back to the heuristic.
- **P5 — Confidentiality in outreach.** Reminder messages never mention HIV/ART.
- **P6 — Measure before trusting.** No model is promoted to production scoring until it beats the
  heuristic on local validation. No central distribution before single-site proof.

---

## 3. System Topology (current → target)

### 3.1 Current (data path — already in production)
```
EMR  ──►  WINCO (facility server)  ──►  Mobile App (Room DB, on-device scoring)
```

### 3.2 Target (knowledge path — to build)
```
                         ┌───────────────────────────────────────────┐
                         │   CENTRAL KNOWLEDGE SERVER (aggregator)    │
                         │   - gates + FedAvg-aggregates models       │
                         │   - validates on benchmark, versions+signs │
                         └───────────────▲───────────────┬───────────┘
                          model push      │               │  global model publish
                       {weights,n,AUC,ver}│               ▼
        ┌───────────────────────┐   ┌───────────────────────┐   ┌───────────────────────┐
        │ WINCO facility A node │   │ WINCO facility B node │   │ WINCO facility C node │
        │ trains+validates local│   │ ...                   │   │ ...                   │
        └───────────▲───────────┘   └───────────────────────┘   └───────────────────────┘
          model sync │ (like data sync)
                     ▼
        ┌───────────────────────┐
        │  Mobile Apps (edge)   │  scores patients with current global model
        │  fall back to heuristic│  + local guardrail; logs outcomes
        └───────────────────────┘
```

**Three roles, ONE WINCO codebase, TWO deployments:**

| Tier | Role | Responsibilities |
|---|---|---|
| **App (edge)** | Scorer | Pull current global model (like a data sync); score on-device; log reminder outcomes; heuristic fallback. |
| **WINCO (facility node)** | Trainer + relay | Train/validate model on facility data; push `{weights, n, AUC, schema_version}` to central; pull global model; serve it to apps. |
| **Central Knowledge Server** | Aggregator | Gate submissions; FedAvg-aggregate; validate on benchmark; version + sign; publish to all facility nodes. |

**Decision:** the central knowledge server is the **same codebase as WINCO, deployed separately**
(multi-tenant aggregator mode). Do **not** merge it into a single-tenant facility instance.

**Decision:** training happens **server-side on WINCO** (it holds the full facility dataset; apps
share the same synced data so per-phone training is redundant). The in-app Kotlin trainer is a
bootstrap/reference; port the ~30 lines of logistic regression to WINCO's stack for production.

### 3.3 Production deployment & role differentiation

Same WINCO artifact deployed twice; the role is selected by **`KNOWLEDGE_ROLE`** (+ env). Apps only
ever talk to their facility node — never to central.

| Dimension | **Facility node** (`KNOWLEDGE_ROLE=node`, default) | **Central aggregator** (`KNOWLEDGE_ROLE=aggregator`) |
|---|---|---|
| EMR connection | has `EMR_DATABASE_URL`; mirrors patient data | **none** — never connects |
| Patient data (PHI) | full EMR mirror | **zero PHI** — only weights + metrics |
| Serves mobile apps | **yes** | **no** (only nodes call it) |
| Core action | **trains** per-facility LR; compares local vs global; serves champion | **aggregates** (FedAvg); gates; signs; publishes — **never trains on data** |
| DB contents | EMR mirror + `risk_model` rows | `model_submission` + global `risk_model` rows |
| Active endpoints | node + app endpoints; acts as **client** of central | `POST /submit`, `GET /global`, aggregate scheduler |
| Signing key | **public** (verify only) | **private** (signs global) |
| Network | reachable by its apps; outbound to central | publicly reachable by all nodes (one URL) |

**One-line principle:** *the node is where data lives and models are trained; the aggregator never
sees data and only combines models.* That is the privacy guarantee — only ~10 numbers travel up.

**Knowledge cycle (nightly):** node trains per facility → **pushes** each model up → central
**gates + FedAvgs + signs + publishes** a global model → node **pulls** it, **verifies signature**,
**compares** global vs local on its own data (champion-by-local-AUC), serves the champion to apps;
the app's own guardrail is the final safety net. A 90-patient facility almost always finds global
wins; a 5,000-patient facility keeps local when local still wins — small sites reach big-site
accuracy without ever being harmed.

**FedAvg correctness (D1 resolved):** each node's LR is standardised differently, so the aggregator
converts every submission to **raw-feature coefficients** (`w_raw = w/σ`, `b_raw = b − Σ w·μ/σ`),
FedAvgs the raw coefficients weighted by `n_samples`, and publishes the global model with **identity
scaling** (μ=0, σ=1). The app/node `probability()` then serves it unchanged.

---

## 4. The Knowledge Packet (model artifact)

The unit that travels between tiers. Small (~KB). Versioned + signed.

```json
{
  "schema_version": "feat-v1",
  "global_version": 42,
  "model_type": "logistic_regression",
  "feature_names": ["priorIitEpisodes","priorLatePickups","lateRate","visitsSoFar",
                    "ageYears","isPaediatric","refillPeriodDays","distanceKm","hasPhone"],
  "weights": [ ... ],
  "bias": 0.0,
  "scaling": { "mean": [ ... ], "std": [ ... ] },     // or raw-feature coefficients
  "training": { "facility_hash": "...", "n_samples": 4187, "auc": 0.81, "created_at": "..." },
  "signature": "..."                                   // central server signs the global model
}
```

> **Critical:** models can only be aggregated/averaged within the **same `schema_version` and
> feature scaling**. To FedAvg, either fix a global scaling or convert each facility's standardized
> weights back to raw-feature coefficients before averaging. This is the #1 thing that breaks naïve
> implementations.

---

## 5. Aggregation & Adoption Policy

- **Aggregation = Federated Averaging (FedAvg)**, weighted by sample count:
  `global_w = Σ(nᵢ·wᵢ) / Σ(nᵢ)`. Large facilities dominate proportionally; small facilities
  receive the pooled intelligence → equal accuracy regardless of size (the project goal).
- **NOT pure champion-challenger.** "Best single model wins" can overfit one population and hurt
  small/rural sites. (Champion-challenger is acceptable only as a Phase-2 simplification.)
- **Local guardrail (adoption):** a facility/app adopts a new global model **only if it does not
  underperform the local model on local recent data** (measured by the existing backtest). Global
  by default, with a safety net.

### Quality gates (central server, every submission)
- Minimum sample count (e.g. ≥ N labelled past appointments).
- Validation on a central held-out **benchmark** set (reuse the backtest harness).
- Bounded weight norms / outlier rejection (anti-poisoning).
- New global model must be **≥ previous** global on the benchmark before publishing.

### Trust & privacy hardening (later phases)
- Central server **signs** the global model; facilities/apps verify before adopting.
- **Differential privacy** (calibrated noise on shared weights) + **secure aggregation** so shared
  weights can't leak facility-level info.

---

## 6. What's Already Built (Phase 0 — DONE)

On-device, offline, explainable. All compile-verified via `:app:assembleDebug`; **NOT yet
device-tested**.

### 6.1 Risk engine & assessment
- `utils/PatientRiskEngine.kt` — explainable scorer. Dimensions: Adherence, History/Pattern,
  Access/Distance, Virologic/EAC, TB/TPT, Identity(biometric), PMTCT, Demographic.
  Bands: CRITICAL ≥75, HIGH ≥50, MODERATE ≥30, LOW. `IIT_THRESHOLD_DAYS = 28`.
  `PatientSignals` (nullable superset), `PatientRiskScore`, `analyzeHistory()`.
- `utils/GeoUtils.kt` — `haversineKm`, `parseCoord`.
- `data/risk/RiskAssessmentService.kt` — single source of truth. `observe()` (UI),
  `assessOnce()` (worker). Feeds engine REAL signals from existing app logic:
  - VL: `ViralLoadEligibilityEngine.evaluate(...)` → `viralLoadOverdue` + `patient.lastViralLoadResult`.
  - TB: `patient.lastTbScreeningStatus/Date` → symptomatic / overdue.
  - Biometric: `getAllBiometrics()` (archived excluded) → `biometricEnrolled`.
  - Distance: facility GPS (prefs) + patient lat/lng.
  Returns `RiskAssessment{forecast, approaching, established, all}`, `AssessedClient{score,type,target,context}`.

### 6.2 Predictive tiers
- **Forecast** (upcoming appt, predicted to miss; UI shows MODERATE+).
- **Approaching IIT** (1–27 days late; countdown to day 28).
- **Established IIT** (>28 days late).

### 6.3 Reminders & outreach
- `data/messaging/ReminderGateway.kt` — Termii (SMS) + SendGrid (email); neutral wording;
  Nigerian MSISDN normalisation. `data/network/MessagingApiService.kt` (absolute `@Url`),
  `di/MessagingModule.kt` (dedicated plain OkHttp — separate from WINCO host-rewrite graph).
- `data/messaging/ReminderTemplates.kt` — `ReminderType` {FORECAST, APPROACHING_IIT,
  ESTABLISHED_IIT}, default + saved templates, placeholders `{name} {date} {facility} {days}`.
- **AI-controlled cadence** — `data/reminder/ReminderScheduler.kt`. Pre-appointment offsets by band:
  CRITICAL {14,7,3,1,0}, HIGH {7,3,1}, MODERATE {3,1}, LOW {1}; approaching = daily; established =
  weekly. `todayEpochDay()` (WAT), `candidates()`, `ReminderAudience{GENERAL, AT_RISK}`.
- `data/reminder/ReminderWorker.kt` — `@HiltWorker`, self-rescheduling (12h/24h), audience-aware,
  epoch-day de-dupe.
- Settings: gateway config, "Appointment Reminders", "Automatic Reminders" (toggle + audience +
  interval + per-type template editors).

### 6.4 Measurement loop (#2 — DONE)
- `data/database/entities/ReminderLog.kt` + `ReminderLogDao` + `ReminderAuditLogger.kt`. DB
  **version 17**, `MIGRATION_16_17` (added to BOTH `AppDatabase.getInstance` AND `DatabaseModule`).
- `ReminderLogScreen` — delivery rate + **attendance rate** (reconciled vs pharmacy visit on/after
  appointment date).

### 6.5 Validation & learning (#1 — DONE)
- `data/risk/LogisticRegression.kt` — dependency-free, standardised, L2 GD, interpretable.
- `data/risk/RiskBacktestService.kt` — longitudinal samples from pharmacy history (label = next
  visit >28d late); **patient-level 70/30 split** (no leakage); heuristic vs learned;
  AUC/sensitivity/specificity/PPV + learned feature weights.
- `ModelValidationScreen` — run on demand; reachable from AI screen top-bar.

### 6.6 Model feature vector (v1 — `feat-v1`)
`[priorIitEpisodes, priorLatePickups, lateRate, visitsSoFar, ageYears, isPaediatric,
refillPeriodDays, distanceKm, hasPhone]` · **Label:** next visit > 28 days late.

### 6.7 Persistence keys / infra
- Prefs: facility GPS (`facility_lat_<id>`…), gateway keys (termii/sendgrid/sender/from),
  auto-reminder (`auto_reminder_enabled|interval_hours|audience`), templates (`reminder_tpl_<key>`),
  dedupe (`reminder_sent_<uuid>` = epoch-day). Stored in EncryptedSharedPreferences.
- DB version **17**. Nav routes: `AiInsights`, `ReminderLog`, `ModelValidation`.
- Manifest: `ACCESS_FINE/COARSE_LOCATION`.

---

## 7. Roadmap (phased; each phase independently useful)

> Rule (P6): **prove the model beats the heuristic on one real dataset (ModelValidation) before
> building any central infrastructure.**

- [x] **Phase 0 — On-device AI module, reminders, audit, validation harness.** (done, compile-only)
- [~] **Phase 0.5 — Live validation.** SERVER DONE 2026-06-26: migrations applied to `hmis_db`
      (created `risk_model`+`model_submission`, stamped Alembic to single head `b2e4submission02`);
      trained facility 1738 on real data → **n=7668 appts, IIT base rate 9.9%, 5-fold CV AUC 0.641
      vs 0.521 baseline → can_adopt=true**. Active model row serving. PENDING (device/user): facility
      GPS capture, gateway keys, on-device pull + adopt, reminder send + attendance reconciliation.
- [x] **Phase 1 — Adopt-learned-model (in-app, no backend).** (done 2026-06-26, compile-only)
      - `RiskFeatures` = shared feature builder (train/serve parity; `feat-v1`).
      - `LogisticRegression` exposes mean/std; `RiskBacktestService` now does **5-fold patient-level
        CV** (out-of-fold metrics) + trains a final model on all data.
      - `LearnedRiskModel` + `ModelStore` persist the model (Gson→prefs) and enforce the **guardrail**
        (`activeModel()` returns null unless mode=LEARNED, schema matches, and learned AUC ≥ heuristic
        AUC). Adopt only offered when `canAdopt` (learned AUC ≥ heuristic AND ≥ 0.60).
      - `RiskAssessmentService` applies the learned model to **forecast clients only** (score=p×100,
        band via `bandForProbability`, prepends an "AI model forecast risk: N%" factor; heuristic
        factors retained as explanation). Approaching/established stay heuristic. Falls back to
        heuristic if no/incompatible model.
      - `ModelValidationScreen`: 5-fold metrics, learned weights, active-mode banner, Adopt/Revert.
      - PENDING device test: run on real synced data to confirm `canAdopt` behaviour + that the
        learned model actually beats the heuristic before relying on it.
- [x] **Phase 2 — WINCO facility-node training + serving + app pull.** (done 2026-06-26)
      - `models.py` `RiskModel` (`risk_model` table) + `to_packet()`.
      - Alembic **merge migration** `a9d1riskmodel01` joins the two open heads
        (`1c9d7e8f0a12` + `b4e9f12c7a01`) AND creates `risk_model`. **Run `flask db upgrade`.**
      - `services/risk_training.py`: numpy LR mirroring Kotlin; builds the SAME `feat-v1` features per
        `facility_id`; 5-fold patient-split CV; baseline (late-pickup-rate) AUC guardrail; supersedes
        prior active, bumps version.
      - `routes/knowledge_api.py` (`/api/knowledge`, `_api_guard`): `GET /model/current`, `POST /train`,
        `GET /models` + nightly `start_knowledge_scheduler` (24h); registered in `app.py`.
      - App: `WincoModelPacket` + `WincoApiService.getRiskModel()`; ModelValidation "Pull from WINCO"
        → maps packet → `LearnedRiskModel` → `ModelStore` (guardrail/schema still gate use; missing
        server AUC fails safe).
      - Verified: Python `py_compile` + import (feature names == app `feat-v1`); app `assembleDebug`
        SUCCESSFUL. **NOT run against live WINCO DB** — migration + first train + end-to-end pull are
        the server/device test.
- [x] **Phase 3 — Central aggregator + node push/pull/compare.** (done 2026-06-26)
      - `KNOWLEDGE_ROLE` switch (node|aggregator) + federation env in `config.py`.
      - `services/knowledge_core.py` — FedAvg in raw-feature space (D1), HMAC-SHA256 sign/verify,
        proba/AUC. **Unit-tested:** raw-conversion exact (Δprob ~2e-16), larger facility dominates,
        tamper rejected.
      - `services/knowledge_aggregator.py` — gates submissions (min-n, AUC floor, weight-norm),
        FedAvgs survivors, signs, publishes global `risk_model` (scope='global', identity scaling).
      - `services/knowledge_sync.py` — node push (urllib) + pull + verify + **per-facility champion
        selection** (local CV-AUC vs global-on-local-AUC). If global wins, the local row is superseded
        so `/model/current` falls back to the global champion.
      - `models.py` `ModelSubmission` + merge-safe migration `b2e4submission02`.
      - `routes/knowledge_api.py` — `_service_guard` (node↔central token); aggregator endpoints
        `POST /submit`, `GET /global`, `POST /aggregate`; role-branched scheduler (node: train→push→
        pull→compare; aggregator: FedAvg). **App needs NO change** — it still pulls `/model/current`,
        now served the champion.
      - Verified: py_compile + import + an end-to-end FedAvg→sign→verify→champion simulation.
      - **CAVEAT / future:** global-on-local eval isn't fully leakage-free (the facility contributes to
        the average it's scored against); honest local side uses stored CV-AUC. A fold that excludes the
        facility from the global during eval is a refinement. **NOT run against live WINCO DBs.**
      - App pulls global model via existing sync mechanism.
- [ ] **Phase 4 — Privacy/robustness hardening.** Differential privacy on shared weights; secure
      aggregation; rollback; monitoring/drift detection.
- [x] **Phase 5 — Accuracy uplift (IIT).** feat-v2 (+VL) DONE end-to-end. **EBM evaluated, NOT adopted**
      — only +0.005 over LR and breaks federation (+0.10); LR retained as the best practical model.
      Remaining: calibration + temporal/fairness validation gates (spec §12.2).
- [ ] **Phase 6 — Multi-outcome platform.** Feature store, outcome registry, Viral-non-suppression +
      AHD heads first. (spec §12)
- [ ] **Phase 7 — Co-infection heads + CDS pathways.** Incident TB, CrAg/crypto, HepB/C, cervical;
      decision-support actions + national dashboards. (spec §12)
- [ ] **Phase 8 — Continuous learning & governance.** Drift-triggered retrain, model cards, fairness
      monitoring — position as the GON/NGO service-delivery gateway. (spec §12)

---

## 8. WINCO Review Checklist (when directory + DB URL provided)

Goal: confirm WINCO can serve as (a) facility node and (b) aggregator mode, and design the model
APIs to mirror existing patterns.

- [ ] **Tech stack/framework** (Flask/Django/FastAPI?) and structure of existing endpoints
      (`/api/art/token`, the sync/list endpoints the app already calls).
- [ ] **Auth model** (Bearer token flow) — reuse for model push/pull.
- [ ] **Multi-tenancy** — facility_id boundary; can one instance safely run aggregator mode, or
      separate deployment needed (expected: separate).
- [ ] **DB schema** — where to add `model_registry` (version, schema_version, weights blob,
      n_samples, auc, signature, status, created_at) and `model_submissions` (per-facility).
- [ ] **Scheduled jobs** — existing mechanism for periodic retrain/aggregate?
- [ ] **Training feasibility server-side** — can WINCO read the facility ART pharmacy history to
      build the same `feat-v1` dataset and train the LR?
- [ ] **Outbound connectivity** — can a facility node reach the central server (firewalls, on-prem)?

### Proposed new endpoints (to confirm against WINCO conventions)
```
# Facility node ↔ Central
POST  /api/knowledge/model            # facility → central: submit local model + metrics
GET   /api/knowledge/model/current    # facility ← central: pull current signed global model
# App ↔ Facility node
GET   /api/knowledge/model            # app ← facility: pull current model (sync-style, versioned)
```

---

## 8A. WINCO Review Findings & Integration Plan (reviewed 2026-06-25)

Reviewed `C:\Winco`. **Verdict: WINCO is a strong fit and can host both roles.**

### Stack & infra
- **Flask + Flask-SQLAlchemy + Flask-Login + Flask-Migrate (Alembic) + PostgreSQL** (`hmis_db`);
  reads a separate **read-only EMR DB** (`mfamosing`). `pandas`/`numpy` present; **no scikit-learn yet**.
- Two DBs in `.env`: `DATABASE_URL` (app) + `EMR_DATABASE_URL` (source). *Security: `.env` ships a
  weak default `SECRET_KEY` and DB password — must be rotated in prod, because `SECRET_KEY` signs the
  mobile tokens (and would sign model artifacts).*

### Checklist results
- **Auth (✓):** `itsdangerous.URLSafeTimedSerializer` Bearer token (salt `mobile-api-token`, 7-day),
  issued by `POST /api/art/token`, enforced by the `_api_guard` decorator. **Reuse verbatim** for
  model push/pull.
- **Multi-tenancy (✓):** everything is facility-scoped. `EmrFacility.source_id` is the canonical
  facility id; EMR tables (`emr_hiv_art_pharmacy`, `_enrollment`, `_status_tracker`, `biometric`)
  all carry `facility_id`. User roles = admin/national/lga/facility. WINCO already resolves an
  `active_facility_id`.
- **Scheduled jobs (✓):** `start_auto_sync_scheduler()` runs a daemon thread on an interval
  (`EMR_AUTO_SYNC_INTERVAL_MINUTES`). **Reuse this pattern** for nightly train/aggregate.
- **Server-side training feasibility (✓✓):** `EmrHivArtPharmacy` has exactly the `feat-v1` fields —
  `person_uuid, facility_id, visit_date, next_appointment, refill_period, adherence, dsd_model,
  latitude, longitude, archived`. WINCO already computes IIT/TX_CURR with the **same 28-day logic**
  (`_is_tx_curr_active`, `_art_care_category`, `_iit_weekly_count`) and exposes a `pharmacy-history`
  endpoint. The backtest dataset + label can be built server-side by reusing these query patterns.
- **Facility GPS (note):** `EmrFacility` has name/lga/state but **no coordinates** — confirms the
  in-app facility-GPS capture remains the source until a coord column is added.
- **Existing AI guide (✓):** `AI_HIV_AI_Implementation_Guide_Nigeria_Africa.md` already endorses our
  exact approach — IIT-first, interpretable models (LR/GBM+SHAP), risk bands, top-3–5 explainability,
  daily batch scoring, **edge-enabled mode with periodic sync**. Align with it.

### Resolved decisions (from this review)
- **D2 → RESOLVED:** training happens **server-side on WINCO** (it holds the full per-facility
  pharmacy history and already computes the same cohort logic). The in-app Kotlin LR is the
  reference; port to Python (add `scikit-learn`, or pure-numpy to mirror our trainer).
- **Scoring stays on-device** (offline-first / WINCO guide's "edge-enabled mode"); WINCO trains +
  distributes the model artifact; the app scores locally and falls back to the heuristic.
- **Label alignment:** reconcile our per-appointment label (next visit >28d late) with the WINCO
  guide's `iit_event_next_30d`. Pick one canonical definition before training (lean: keep
  per-appointment >28d late = IIT, which matches PEPFAR TX_ML and the app's own logic).

### WINCO build plan (Phase 2/3)
- **New blueprint** `routes/knowledge_api.py`, prefix `/api/knowledge`, guarded by `_api_guard`:
  - `GET  /api/knowledge/model/current?facility_id=` → app/facility pulls current signed model.
  - `POST /api/knowledge/model` → facility node submits local model to central (aggregator mode).
- **New tables** (Alembic migration, in `models.py`):
  - `model_registry(id, scope, facility_id, schema_version, global_version, model_type, weights JSONB,
    bias, scaling JSONB, feature_names JSONB, n_samples, auc, status, signature, created_at)`.
  - `model_submission(...)` (per-facility incoming models when in aggregator mode).
- **New service** `services/risk_training.py` — **loops per `facility_id`** the node hosts; builds
  the `feat-v1` dataset from `emr_hiv_art_pharmacy` (+ patient/enrollment), trains LR, validates
  (patient split / k-fold), writes a per-facility row to `model_registry`. Facility key = DATIM code
  (D6).
- **Central aggregator (separate deployment)** — receives per-facility submissions from all nodes;
  quality-gates; FedAvg across **all facilities** weighted by `n_samples`; benchmark-gates; signs;
  publishes one global model. The unit aggregated is the facility, so a 5,000-patient facility and a
  90-patient facility both contribute and both receive the global model.
- **Scheduled hook** — extend the auto-sync scheduler: nightly per-facility train on each node; on
  the aggregator instance, a periodic FedAvg + benchmark-gate + sign + publish step.
- **App side** — add a model-pull to the existing sync (tiny payload); `RiskAssessmentService` uses
  pulled weights under the local guardrail (Phase 1 toggle already planned).

### Topology nuance discovered
WINCO is **multi-facility-capable but currently single-EMR**. So it can act as a **facility/region node**
*and* be deployed separately as the **central aggregator** (same codebase, `knowledge` blueprint +
aggregator mode flag). This confirms the "one codebase, two deployments" recommendation. **Open: is
WINCO deployed per-facility or per-state/national?** (drives whether the aggregator is a separate
instance or a mode of an existing state WINCO) — see D5 below.

## 9. Open Decisions / Risks

- **D1 — FedAvg scaling:** fix a global feature scaling vs convert to raw-feature coefficients
  before averaging. (Leaning: raw-feature coefficients.)
- **D2 — Training location final call:** server-side WINCO (recommended) vs designated-device.
- **D3 — Benchmark set ownership:** how the central server obtains a fair, representative held-out
  benchmark without centralising PHI (candidate: each facility contributes only aggregate metrics;
  central validates by sending the model *down* for local eval and collecting metrics up).
- **D4 — Federation scale:** only worth the infra at ~tens of facilities. Below that, Phase 1 +
  manual signed export delivers ~80% of value at ~10% of cost.
- **D5 — WINCO deployment shape → RESOLVED (2026-06-25):** WINCO is deployed **per-EMR**, but a
  single EMR can contain **multiple facilities**, so each WINCO node is **multi-facility**. Chosen
  topology = **multi-facility WINCO nodes + a SEPARATE hosted central aggregator** (Option 1's
  separate central server, adapted for multi-facility nodes). **The unit of knowledge is the
  FACILITY (facility_id), not the WINCO instance.** A node trains one model per facility_id it hosts
  and submits each upward; central aggregates across all facilities from all nodes into one global
  model (FedAvg) and publishes back; each facility adopts global under the local guardrail.
- **D6 — Global facility identity → RESOLVED (+ provisioning 2026-06-26):** `EmrFacility.source_id`
  is unique only within one EMR/WINCO and can collide across nodes. Use the **DATIM code**
  (globally-unique PEPFAR facility id) as the federation key. **DATIM codes are configured on the
  NODE, never central** — central is data-blind and only ever sees the opaque DATIM key on each
  submission. **`facility_id` and DATIM are different ids at different levels** — `source_id` is local
  to one EMR; DATIM is the global PEPFAR site id (the federation key). Provisioning on the NODE, in
  priority order: (1) **auto-derived** by `resolve_datim()` from `NdrClientMatch.datim_code` (already
  populated by the NDR/PBS line-list upload); (2) env override `KNOWLEDGE_FACILITY_DATIM` (JSON
  `{"<source_id>":"<DATIM>"}`); (3) else aggregator falls back to `fid:<id>` (OK single-node, collides
  across nodes). Verified live: facility 1738 → DATIM `gNfPmS9Pmbr` auto-resolved from 969 NDR clients.
- **R5 — Secrets:** WINCO `.env` default `SECRET_KEY` + DB password must be rotated before any
  model signing/distribution (the same key signs mobile tokens and would sign models).
- **R1 — Model poisoning** (mitigated by quality gates).
- **R2 — Schema drift** across app versions (mitigated by `schema_version` refusal).
- **R3 — Confidentiality of SMS** (neutral wording; add patient consent flag — Phase 1+).
- **R4 — API keys in app** (move gateway sends to WINCO proxy in production).

---

## 10. Glossary

- **IIT** — Interruption in Treatment; PEPFAR/NACA: a missed ART pickup becomes IIT on the **29th
  day** (>28 days late).
- **EAC** — Enhanced Adherence Counselling (3 sessions for unsuppressed clients before VL recheck).
- **Federated learning** — train locally, share models not data; a central server aggregates.
- **FedAvg** — Federated Averaging; sample-count-weighted average of model weights.
- **Champion-challenger** — keep whichever model scores best on a benchmark (simpler, less robust).
- **Local guardrail** — adopt a new/global model only if it doesn't underperform locally.
- **Knowledge packet** — the signed, versioned model artifact (§4).

---

## 12. Accuracy & Multi-Outcome Roadmap (future specification)

**Goal:** evolve from a single IIT forecaster into the **national gateway for predictive HIV service
delivery** — accurate, explainable, federated predictions across every outcome HIV programs act on,
that GON and NGOs rely on. Invariants P1–P6 still hold (share models not data; explainable; federated).

### 12.1 Accuracy uplift (same IIT outcome, better model) — Phase 5

> **Empirical results (2026-06-26) — every predicted lever confirmed on REAL data.**
> | Config | Data | Model | CV AUC |
> |---|---|---|---|
> | feat-v1 | dev facility 1738 (n=7,668) | LR | 0.640 |
> | feat-v1 | **Holy Family (n=88,394)** | LR | **0.746** — *more data: +0.106* |
> | feat-v1 **+ VL** | Holy Family | LR | **0.768** — *VL feature: +0.022 (98% coverage)* |
> | feat-v1 + VL | Holy Family | **HistGradientBoosting** | **0.783** — *algorithm: +0.015* |
>
> **Cumulative 0.640 → 0.783.** (1) **MORE DATA is the single biggest lever (+0.106)** — validates the
> whole federation effort. (2) **VL is genuine signal** (parse `laboratory_result` joined to
> `laboratory_test` where `lab_test_id=16`; '0'=suppressed; guard negatives/garbage). (3) **GBM beats
> LR** via interactions. Counter-finding: more *pharmacy-derived* features did NOT help LR (0.639→0.632)
> — lift comes from CLINICAL signal + algorithm. CD4 (`lab_test_id=1`), EAC (`hiv_eac*`), regimen,
> WHO-stage (`hiv_art_clinical`) still to add → likely >0.80. *Source: Holy Family training DB (raw EMR
> schema, un-prefixed tables; READ-ONLY, never pulled into WINCO).* Caveat: CV AUC is retrospective —
> add temporal validation before deployment claims.

Levers in order of impact:
1. **Feature engineering v2** (biggest ROI). Add, mostly from data WINCO already mirrors:
   - *Temporal:* ART tenure, days-since-last-visit, lateness trend & variance, longest prior gap,
     recency-weighted lateness, MMD drugs-on-hand coverage, month/season.
   - *Regimen:* line (1st/2nd/3rd), DTG vs other, regimen switches, DSD model, MMD type.
   - *Clinical:* last VL + suppression + VL trend, CD4 + trend, WHO stage, weight/BMI trend, TB-screen
     status, OI/AHD history, EAC sessions completed.
   - *Social/access:* age band, sex, pregnancy/breastfeeding, key-population, distance, phone
     reachability, prior tracing outcome, prior return-to-treatment (RTT).
   - *Missingness as signal:* explicit "missing" indicators — never silent zero-fill.
2. **Algorithm:** upgrade plain LR → **Explainable Boosting Machine (EBM / GA²M)** or gradient-boosted
   trees: typically **+0.05–0.15 AUC** on tabular clinical data. Prefer **EBM** — glass-box + additive
   (so it stays explainable AND FedAvg-friendly). Keep LR as the simple federated baseline.
3. **Calibration:** isotonic/Platt; report **Brier score + reliability curves** so probabilities are
   trustworthy for triage thresholds.
4. **Imbalance/triage:** optimise **precision@k / recall at clinic capacity** (not raw accuracy); tune
   the operating threshold to actual staffing.
5. **Time-to-event:** reframe as **discrete-time survival** (hazard over next 30/90/180 days) —
   predicts *when*, which is far more actionable than a static label.

### 12.2 Validation & trust (required before GON/NGO reliance)
- **Temporal validation** (train on past → test on future), not only random k-fold.
- **Calibration** (Brier, reliability) + **decision-curve / net-benefit** at the operating point.
- **Subgroup fairness** by sex/age/region/key-population (FP/FN parity); documented bias audit.
- **External validation** across facilities — the federation provides this for free.
- Versioned **model cards** per outcome (data, metrics, fairness, intended use, limitations).
- **Automate it as a release GATE — DONE 2026-06-26 (WINCO `services/risk_validation.py`).** `train_facility`
  now computes calibration (Brier + ECE), **temporal validation** (train earliest 70% by date, test
  latest 30%), **subgroup fairness** (per-sex + per-age-band AUC + FP/FN parity gaps), and **net benefit**
  at the 0.30 operating point. **The gate is real:** `can_adopt = discrimination_ok AND temporal_ok
  (temporal AUC ≥ floor) AND fairness_ok (min subgroup AUC ≥ floor−0.05)`. A model that FAILS is stored
  `status='rejected'` and the previous good model **stays active** (never served a failing model). Full
  report stored in `risk_model.validation` (migration `d4a6validation04`) + in `to_packet()`. Verified
  facility 1738: passed (CV AUC 0.657, Brier 0.083, ECE 2.2%; temporal AUC 0.619; min subgroup 0.572) —
  and it correctly surfaced temporal drift (ECE 12.9% on future data) + a weak subgroup.

### 12.3 Multi-outcome platform — Phase 6+
Generalise from one outcome to many, each an independent, federated "prediction head":
- **Outcome registry:** add an `outcome` column to `risk_model` + `model_submission` (key = outcome ×
  facility × schema_version). Each outcome federates independently via the existing FedAvg path.
- **Feature store:** one versioned pipeline computes features once, reused by all heads (consistency +
  maintainability). Per-outcome **eligibility** gates who is scored (e.g., VF model only for ART ≥6mo
  with a VL due).
- **Decision-support pathways:** every prediction maps to a NACA/WHO **care action** (the "gateway"
  value) — not just a score. Risk-stratified worklists per outcome.

### 12.4 Outcome catalog (label · horizon · key features · WINCO source · action)
| Outcome | Label (NACA/WHO-aligned) | Horizon | Key features | Source | Action |
|---|---|---|---|---|---|
| **IIT** (live) | pharmacy gap >28d | next appt | pickup pattern, demo, distance | art_pharmacy | trace / remind |
| **Viral non-suppression** | next VL ≥1000 c/mL | next VL | VL hx, adherence, EAC, regimen, tenure | lab_result / viral_load | EAC + repeat VL |
| **Virological failure** | 2× VL ≥1000 post-EAC | 6–12 mo | VL trajectory, EAC completion, switches | lab_result | switch assessment |
| **AHD** | CD4 <200 or WHO 3/4 (all <5y) | presentation / 6 mo | CD4 trend, VL, BMI, OI hx, late presentation | lab_result, art_clinical | CrAg, TB-LAM, prophylaxis |
| **Incident TB** | active TB dx | 6 mo | TB-screen hx, symptoms, CD4, VL, TPT, nutrition | art_clinical, observation | intensified case finding, TPT |
| **CrAg+ / crypto** | serum CrAg+ | at CD4<200 | CD4, AHD, symptoms | lab_result | CrAg screen, fluconazole |
| **HepB/C · STI · cervical** | dx / screen-positive | program cadence | demo, risk, comorbidity | observation / lab | targeted screening |
| **Poor outcome (LTFU/death)** | composite | 6–12 mo | engagement, clinical, social | multi | escalate case management |

### 12.4b VL identification — precision vs recall (IMPORTANT, 2026-06-26)
How VL is distinguished from other lab tests matters a lot:
- **Correct method = filter by TEST type:** `laboratory_result` → `laboratory_test` where
  `lab_test_id = 16` ("Viral Load"). Precise AND complete. *The experiment AUCs (0.768/0.783) used
  this.*
- **WINCO's current method = SAMPLE type `sample_type_id = 5` (Plasma):** used by both WINCO's VL
  serving (`lastViralLoadResult`, art_api_routes ~L1581) AND the feat-v2 trainer (for parity).
  Empirical check (Holy Family): Plasma is **100% precise** (only VL), BUT **misses ~65% of VL
  results** (19,002 of ~29,000 VL results have no linked Plasma sample) → **low recall.**
- **FIX — DONE 2026-06-26:** mirrored `laboratory_test` → `EmrLaboratoryTest` (migration
  `c3f5labtest03`) + emr_sync entry (`laboratory_test`, priority 95). Both the trainer (`_vl_history`)
  and serving (`_latest_lab_vl_summary`, `_lab_vl_history`) now identify VL by **`lab_test_id=16`**
  via the catalog, with a **Plasma fallback** (`_vl_catalog_ready()`) so nothing breaks until the
  catalog syncs. Verified on dev: VL recall **1,574 → 3,615** results (648 → 935 patients), facility-1738
  AUC **0.645 → 0.657**. Parity preserved (training + serving use the same identification; the app's
  `lastViralLoadResult` is now complete too). VL_LAB_TEST_ID=16 (CD4=1) constant in both files.

**VL TYPE (`laboratory_test.viral_load_indication`, integer → codeset group `VIRAL_LOAD_INDICATION`):**
Baseline=300/1393, **Routine=301/303** (dominant), **Confirmation/Post-EAC=302**, Clinical failure=297/304,
Immunologic failure=305, **PMTCT=306**, recent-infection conf=719, unspecified=0. *VL identification =
TEST type `lab_test_id=16` (authoritative); sample_type (Plasma=5, DBS=4) is only the specimen and is
lossy.* For the model: (1) latest VL value/suppression uses ALL VL types; (2) ADD VL-type features —
"prior Post-EAC/Confirmation VL" (was unsuppressed→EAC = high risk), "PMTCT VL", "failure-indication
VL". The WINCO mirror must carry `lab_test_id` + `viral_load_indication` to do this correctly.

### 12.4c feat-v2 DECISION (Holy Family experiment, 2026-06-26) — keep all builds aligned
Same split, relative deltas: feat-v1 0.744 → **+VL value 0.760 (+0.016)** → +VL type 0.763 (+0.002) →
GBM 0.778 / **EBM 0.777**. Decisions for WINCO + app + training (must match):
- **feat-v2 = feat-v1 (9) + 3 VL-VALUE features only** (vl_known, vl_suppressed, vl_log10). **Do NOT add
  VL-type features to the IIT model** — marginal (+0.002), sparse. Reserve VL-type (Post-EAC/PMTCT/
  failure) for the future VL-failure & AHD outcome heads where they're clinically direct.
- **VL identification = `lab_test_id=16` (ALL VL types), NOT `sample_type_id=5`.** Data-SOURCE fix
  (recall ~35%→~100%); does **not** change the feat-v2 schema (still 12 features) → parity preserved
  across trainer + app + serving.
- **Federated model class = EBM** (matches GBM, averageable, glass-box) — replaces LR when ready.
- **Reproducibility fix:** WINCO trainer fold split uses Python's per-process-salted `hash()` →
  non-deterministic. Switch to a stable hash (`hashlib.md5(uuid)`) so folds are reproducible and
  consistent with the app's stable Java `hashCode`.

### 12.5 Data readiness (WINCO)
Already mirrored & usable: `emr_hiv_art_pharmacy`, `emr_hiv_art_clinical` (WHO stage/weight),
`emr_hiv_status_tracker`, `emr_laboratory_sample/result/sample_type` (VL, CD4), `emr_hiv_observation`,
`emr_biometric`, `ndr_client_match`, app `viral_load_history`. **Work needed:** a feature-extraction
service parsing these (incl. JSONB `raw_payload`) + per-outcome label builders + the feature store.
Confirm CD4 / regimen-line / EAC-session parsing — those are the main gaps.

### 12.6 Federation ↔ algorithm tension → RESOLVED (no accuracy sacrifice)
FedAvg averages **parameters** — clean for linear/LR and additive models, but tree ensembles don't
average. This is **not** an accuracy-vs-federation trade-off; layered way out:
1. **EBM (Explainable Boosting Machine) as the federated workhorse** — additive + pairwise terms,
   within ~1–2% AUC of XGBoost, glass-box. Shape functions are per-bin lookup tables that FedAvg
   **once bin edges are globally aligned** (same trick as the raw-coefficient alignment for LR, D1).
   Closes most of the gap with no loss of federatability/explainability.
2. **Federated boosting (SecureBoost / histogram-FedXGBoost)** for max accuracy — nodes share
   aggregated **gradient histograms per bin**, not data; central builds trees. True federated GBM
   (FATE/NVFlare). Reserve for when EBM isn't enough.
3. **Hybrid: federated additive global base + local non-linear personalisation (GBM/residual).** Small
   sites lean on the strong global; large sites get local boost. Champion-selection generalises to
   pick/blend per facility. No accuracy sacrifice.
4. **Deep temporal models federate natively** (FedAvg was built for neural nets — trees are the
   exception). Use at server tier; distill to an EBM student if explainability is required.
On-device scoring stays light (EBM/LR); heavier models server-side (hybrid).

> **DECISION REVISED → KEEP LR (2026-06-26, decisive).** Under the *corrected* setup (coverage-based
> IIT label + feat-v2 + complete VL), patient-level 5-fold CV on Holy Family (n=88,394): **LR 0.7588 vs
> EBM 0.7641 — EBM only +0.005.** But EBM does NOT FedAvg cleanly (per-facility auto-binning differs),
> so adopting it trades the **+0.10 federation gain** (small facility 0.64→0.75 via pooled data) for a
> **+0.005 algorithm gain** — a net LOSS for small facilities. **∴ LR is the best PRACTICAL model: it
> federates natively (capturing the dominant lever) and is ~0.005 behind EBM locally. LR is already
> implemented (feat-v2 + FedAvg) — no swap.** The earlier "EBM 0.793 > LR 0.778" was under the
> appointment-based label (since corrected) and a leaky split. *Future option only:* a federatable GAM
> (fixed GLOBAL bins → one-hot → LR, so shape functions FedAvg) — only if a real, federation-preserving
> accuracy gain is demonstrated; a leaky-split test of binned-LR did not beat raw LR.

> **Empirically confirmed (2026-06-26, Holy Family feat-v1+VL, test n=25,569):** LR 0.778, **EBM 0.793**,
> GBM 0.795. EBM is **within 0.2% of GBM** while being federatable AND glass-box — the tension is not a
> real trade-off. We get near-GBM accuracy + clean FedAvg + explainability together.

> **EBM ARTIFACT VERIFIED (2026-06-26).** An `ExplainableBoostingClassifier(interactions=0)` (pure GAM)
> serialises to a portable artifact and reproduces `predict_proba` EXACTLY (manual==library AUC 0.6721,
> max prob diff 2.2e-16). **Artifact format:** `{model_type:'ebm', feature_names, intercept,
> terms:[{feature_idx, cuts:[...], scores:[...]}]}` where per main-effect term: `bins_[f][0]` = cut
> points, `term_scores_[t]` = score per bin. **Kotlin scorer:** `logit = intercept + Σ_f
> scores[min(digitize(x_f, cuts)+1, len-1)]` (bin 0 = missing). **EBM SWAP plan:** (1) WINCO trains EBM,
> stores artifact (model_type='ebm') — needs a JSONB `ebm_terms`/`artifact` field on `risk_model`
> (migration) or reuse `weights` JSONB + `bias`=intercept; (2) app `LearnedRiskModel.probability`
> branches on model_type (LR vs EBM bin-scorer); (3) **FedAvg of EBMs requires FIXED GLOBAL BINS** per
> feature (the EBM analogue of fixed LR scaling) so shape functions align and scores average. Single-node
> EBM works without that; federation needs the fixed binning. Keep LR served until EBM is wired both sides
> (don't break the closed loop).

### 12.7 "Number-one gateway" success metrics
Programmatic, not just ML: ↑TX_CURR / ↓TX_ML, ↑VL coverage & suppression, ↓time-to-AHD-package, ↑TB
case finding & TPT completion, % high-risk contacted within SLA, and demonstrated **net benefit at
clinic capacity** — measured via the reminder/outcome audit loop and reported through the federation.

---

## 13. IIT definition consistency (TX_CURR/TX_ML alignment)

- **AI IIT label = COVERAGE-based** (`visit_date + refill_period + grace`), matching PEPFAR TX_ML /
  WINCO `_art_care_category` / app `Patient.calculatePatientStatus` — NOT the appointment-based
  `next_appointment + grace` (they agree ~97%, but coverage is program-authoritative + robust to bad
  next_appointment). Applied to the WINCO trainer label + history.
- **Grace = 28 days → IIT on the 29th day (CORRECTED 2026-06-26).** The constant stays **`28`**; the
  "IIT on day 29" semantics come from the **strict comparison** (active iff `coverage_end >= today`;
  IIT iff `next_visit > coverage_end`). So `visit+refill+28` with `>` ⇒ active through day 28, IIT on
  day 29. (A brief change to `+29` was reverted — it would have made IIT start on day 30.) Confirmed
  consistent at 28 across: WINCO trainer + `_is_tx_curr_active`/`_art_care_category`/`_iit_weekly_count`
  + reporting SQL; app `Patient.calculatePatientStatus`, IIT-screen DAO (`nextAppointment+28`),
  `IITClient`, and `PatientRiskEngine`. facility-1738 AUC 0.645.

## 14. feat-v2 — end-to-end status (2026-06-26)

**WINCO + App both on feat-v2; loop closed.**
- **Schema:** `feat-v2` = feat-v1 (9) + `[VL known, VL suppressed, VL log10]`. App `RiskFeatures.kt`
  ⇄ WINCO `risk_training.py` byte-aligned (VL maths mirrored).
- **IIT label:** coverage-based (`visit+refill+28`, IIT on day 29 via strict `>`), consistent across
  WINCO trainer/reporting + app.
- **VL source:** `lab_test_id=16` test catalog (complete), Plasma fallback until synced — both training
  and serving (`lastViralLoadResult`).
- **App live scoring:** `RiskAssessmentService` feeds `patient.lastViralLoadResult`. **Backtest:**
  `RiskBacktestService` uses VL history (`getViralLoadHistoryForPatients`, bulk DAO) → latest-VL-before
  -appointment (no leakage); heuristic comparison sees same VL.
- Now the app's schema MATCHES WINCO's → the app **accepts + uses** the WINCO feat-v2 model (no more
  heuristic fallback on schema mismatch). App `assembleDebug` SUCCESSFUL.
- **NOT device-tested** end-to-end (pull→adopt→score with real synced VL). **Pending:** EBM swap
  (federated model), promote learned model in production, k-fold in app backtest, central aggregator
  deployment.

## 15. CURRENT STATUS & NEXT PICKUP (read this first, 2026-06-26)

### Architecture in use
**Federated learning, 3-tier, on-device explainable scoring. Model = LOGISTIC REGRESSION (feat-v2).**
- App (edge): pulls the model, scores on-device, heuristic fallback + guardrail.
- WINCO facility node: trains per-facility LR, runs validation gates, serves to app.
- Central aggregator (FedAvg): BUILT, **NOT DEPLOYED** → today runs effectively **single-node /
  per-facility**. Standing it up activates the +0.10 federation lever (biggest win).
- **EBM was evaluated and REJECTED** (only +0.005, breaks FedAvg). LR is the chosen model. (§12.6)

### Accuracy (be precise — random-CV AUC OVERSTATES it; TEMPORAL AUC is the honest, deployable number)
- **Holy Family model (federated, recency-windowed, n≈28,974): CV AUC 0.67, TEMPORAL (forward) AUC
  0.69, ECE 1.6% — deployable, passes all gates.** This is the real number.
- **Why NOT 0.76:** training on all 20yr of history gave random-CV 0.76 but temporal 0.60 (near-random
  forward) — an artifact of mixing eras (IIT prevalence fell ~33%→1% as Treat-All/MMD scaled). The gate
  REJECTED it; the recency window (last 48mo, `TRAIN_WINDOW_MONTHS=48`) fixed it: temporal 0.60→0.69.
  **More data HURT here.** *Lesson: report TEMPORAL AUC, not random-CV AUC.*
- **Dev model (facility 1738, n≈7,668): CV AUC ≈ 0.66** (passes gates).

### Federated training WITHOUT pulling data (IMPLEMENTED + proven on Holy Family)
- `services/risk_training_external.py :: train_from_external_emr(emr_url, facility_id, ...)` reads an
  external EMR READ-ONLY (raw un-prefixed schema), trains feat-v2 + runs the validation gates, and writes
  **ONLY the model** (12 weights + report) into WINCO `risk_model`. **No patient rows ingested** (verified:
  `emr_patient` unchanged at 6,067). The model can then FedAvg into the global model so a facility's
  knowledge reaches all sites without its PHI ever moving — the federated principle, end to end.

### Data provenance (IMPORTANT)
- **Holy Family (`holy_family_17052026`) DATA was NEVER pulled into WINCO.** A MODEL trained on it now
  lives in WINCO (facility_id=1585, scope='facility', weights only, no PHI). (Per owner instruction.)
- **Dev model = facility 1738 (dev `hmis_db`, EMR source `mfamosing`).**
- VL catalog `emr_laboratory_test` populated from `mfamosing` (legit EMR source), not Holy Family.

### Federation deployment modes (BOTH WORKING)
- **Standalone (one WINCO, many facilities) — DONE + verified.** No central server: `run_local_federation()`
  runs each node tick (no `CENTRAL_KNOWLEDGE_URL`). Closes the loop in-process.
- **Cross-instance (many WINCOs) — code DONE, needs infra.** Stand up a WINCO with NO patient DB,
  `KNOWLEDGE_ROLE=aggregator`, the SAME `KNOWLEDGE_SIGNING_KEY`/`KNOWLEDGE_SERVICE_TOKEN` as nodes; each
  node sets `CENTRAL_KNOWLEDGE_URL` → it. Scheduler runs push→aggregate→pull→champion. Setup guide §3.

### Pending (priority order)
1. **Provision the separate aggregator instance** for multi-WINCO federation (infra only; code done).
2. **Rotate secrets (R5)** — default `SECRET_KEY` + DB password before production.
3. **Device-test the closed loop** (real device: pull feat-v2 → adopt under gate → score with synced VL;
   reminder send + attendance reconciliation).
4. **Finish §12.2 polish:** app-side display of the validation report (already in the packet),
   periodic recalibration trigger (temporal ECE 12.9% shows drift), k-fold in app backtest, decision-curve.
5. **Phase 4** — DP on shared weights + secure aggregation + drift monitoring.
6. **Phase 6–7** — multi-outcome heads (viral failure, AHD, incident TB, co-infections) + feature store
   + CDS pathways (the "national gateway" expansion). VL-type features (Post-EAC/PMTCT/failure) reserved
   for these.
7. **Phase 8** — continuous learning + governance/model cards.

## 11. Change Log

- **2026-06-26 (s)** — Gaps closed + standalone federation. (A) Champion-compare now scores BOTH models
  on the facility's CURRENT data (was: stale stored CV AUC → would wrongly keep a stale local model).
  (B) Only models that passed gates AND were trained on the current recency window contribute to the
  global (added `train_window_months` marker + `_eligible_for_global`) — stale/pre-window models no
  longer pollute FedAvg. (C) `run_local_federation()` closes the WHOLE loop on a SINGLE WINCO (aggregate
  local eligible models → global → champion-compare → weak facilities adopt) — node_loop runs it when no
  CENTRAL_KNOWLEDGE_URL. Verified: 1738 (stale, excluded from global) → `/model/current` now serves
  GLOBAL v3 (Holy Family knowledge). Local 0.513 vs global 0.544 → adopt. WINCO + Android app both
  compile clean.
- **2026-06-26 (r)** — Aggregator BROUGHT UP + full round-trip proven on real models. Submitted 1738
  (n=7,668) + Holy Family 1585 (n=28,974) → `aggregate_global()` FedAvg → GLOBAL v1 (36,642 samples,
  HMAC-signed, verify=TRUE). Guardrail on small facility 1738's own data: local 0.513 vs global 0.543 →
  ADOPT GLOBAL. Federation secrets provisioned in gitignored `.env` (KNOWLEDGE_SIGNING_KEY/SERVICE_TOKEN,
  role stays `node`). Dev 1738 recency-retrain was gate-REJECTED (n=4,721 too small, CV 0.55) — prev
  model stays active; 1738 is exactly the facility that needs the global. **Prod still needs a SEPARATE
  aggregator instance** (role=aggregator, same signing key/token, no PHI; nodes set CENTRAL_KNOWLEDGE_URL).
  *Noted refinement:* champion-compare uses stored CV AUC vs global-on-current-data — re-score local on
  current data for apples-to-apples (stored AUC can be stale across eras; see recency finding).
- **2026-06-26 (q)** — Federated training WITHOUT data ingestion: `risk_training_external.py` reads an
  external EMR read-only, writes ONLY the model to WINCO. Proven on Holy Family (no PHI ingested,
  emr_patient unchanged). **Recency window added (`TRAIN_WINDOW_MONTHS=48`)** after the gate caught that
  all-history training (CV 0.76 / temporal 0.60) was an era-mixing artifact — IIT prevalence fell ~33%→1%
  over 2006–2026. Windowed model: CV 0.67 / **temporal 0.69** / ECE 1.6%, passes gates, now active in
  WINCO (facility 1585). Honest deployable accuracy ≈ 0.69 (temporal), not 0.76.
- **2026-06-26 (p)** — Validation GATES implemented (§12.2 DONE): `risk_validation.py` (Brier/ECE,
  temporal, subgroup fairness, net benefit) + `risk_model.validation` JSONB (migration d4a6validation04)
  + real gate in `train_facility` (failing model → rejected, prev stays active). Verified 1738 passes;
  surfaced temporal drift + weak subgroup. Remaining §12.2: decision-curve plots, app-side display of
  the report, periodic recalibration trigger.
- **2026-06-26 (o)** — EBM evaluated & REJECTED. Patient-level 5-fold (coverage label, feat-v2): LR
  0.7588 vs EBM 0.7641 (+0.005), but EBM breaks federation (+0.10). EBM artifact extraction verified
  (would work) but adopting it is a net loss for small facilities. **KEEP LR** (already implemented).
  §12.6 decision revised. Federatable-GAM (fixed-bin) logged as future-only.
- **2026-06-26 (n)** — App feat-v2 DONE: `RiskFeatures` SCHEMA_VERSION=feat-v2 (+3 VL features,
  mirrors WINCO), bulk VL DAO (`getByPersonUuids`/`getViralLoadHistoryForPatients`), live scoring uses
  `lastViralLoadResult`, backtest uses VL-history latest-before (no leakage). assembleDebug SUCCESSFUL.
  App schema now matches WINCO → app uses the feat-v2 model. LOOP CLOSED (§14).
- **2026-06-26 (m)** — VL test-catalog mirror DONE: `EmrLaboratoryTest` + migration `c3f5labtest03` +
  emr_sync entry; trainer & serving identify VL by `lab_test_id=16` (Plasma fallback via
  `_vl_catalog_ready`). Verified: recall 1574→3615, AUC 0.645→0.657, serving smoke-tested. Parity
  intact. NEXT: app feat-v2 (RiskFeatures+scoring+backtest) so the app uses the feat-v2 model.
- **2026-06-26 (l)** — IIT label → coverage-based (visit+refill+grace, = TX_ML), 97% agreement vs
  appt-based on Holy Family. **Grace stays 28 days = IIT on day 29 via strict `>`** (a brief `+29`
  experiment was reverted — it meant day 30). Verified consistent at 28 across WINCO (trainer +
  reporting) and app (status calc, IIT screens, PatientRiskEngine). §13.
- **2026-06-26 (k)** — VL-type experiment (Holy Family): VL VALUE +0.016, VL TYPE only +0.002 → feat-v2
  = VL value only (defer VL-type to VL-failure/AHD heads); EBM 0.777≈GBM 0.778. Decoded VL-type codeset
  (Baseline/Routine/Post-EAC=302/PMTCT=306/failure). Decisions logged §12.4c (incl. lab_test_id=16
  identification + deterministic-hash fix) to keep WINCO+app+training aligned.
- **2026-06-26 (j)** — feat-v2 Stage 1 (WINCO trainer): added 3 VL features (vl_known/suppressed/log10),
  `SCHEMA_VERSION='feat-v2'`, VL via Plasma sample_type_id=5 (parity w/ app serving). Trained facility
  1738 (12 feats, runs OK). **VL identification finding (§12.4b): Plasma is precise but ~35% recall —
  fix = mirror test catalog + filter lab_test_id=16 (forward-compatible, no schema change).**
  INTERIM STATE: WINCO=feat-v2, app=feat-v1 → app safely falls back to heuristic on schema mismatch
  (no live device affected). NEXT: (1) VL recall fix, (2) app feat-v2 (RiskFeatures+scoring+backtest).
- **2026-06-26 (i)** — EBM installed + benchmarked: EBM 0.793 vs GBM 0.795 vs LR 0.778 (Holy Family
  feat-v1+VL) — EBM matches GBM within 0.2% while federatable + glass-box. §12.6 tension empirically
  dissolved. Chosen federated model class = EBM.
- **2026-06-26 (h)** — Phase 5 evidence on Holy Family training DB (88k samples): more data 0.64→0.75,
  +VL 0.768, +GBM 0.783 (sklearn installed). Confirms data + clinical + algorithm levers. Next:
  formalize feat-v2 (+VL, parity-safe) + EBM (federatable). interpret/EBM install pending (network).
- **2026-06-26 (g)** — Federation round-trip PROVEN on real data (facility 1738: push→gate→FedAvg→sign
  →verify→champion; global edged local 0.644 vs 0.640). Phase 5 experiment: pharmacy-temporal features
  don't lift LR (0.639→0.632) → pivot to clinical (VL/CD4, 4107 unused lab rows) + EBM/GBM. User
  offered a 2nd facility DB for real federation + more data.
- **2026-06-26 (f)** — Resolved both flagged tensions (no accuracy sacrifice): §12.6 federation↔algorithm
  → EBM federated workhorse + federated boosting + hybrid personalisation + native deep-temporal FedAvg;
  §12.2 validation → automated release gate (temporal+calibration+fairness+net-benefit), complementary
  to accuracy.
- **2026-06-26 (e)** — Added §12 Accuracy & Multi-Outcome Roadmap (feature-eng v2, EBM/calibration,
  temporal+fairness validation, outcome registry + feature store, outcome catalog for VF/AHD/TB/CrAg/
  co-infections, federation↔algorithm decision, gateway KPIs) + Phases 5–8. DATIM now auto-derives
  from NDR (`resolve_datim`), verified facility 1738 → `gNfPmS9Pmbr`.
- **2026-06-26 (d)** — Live migrations applied to `hmis_db` (`risk_model`+`model_submission`,
  stamped to head `b2e4submission02`); trained facility 1738 on real data (n=7668, AUC 0.641 vs 0.521
  baseline, can_adopt). DATIM provisioning via node env `KNOWLEDGE_FACILITY_DATIM` (D6) wired into
  training. Setup guide written to `C:\Winco\FEDERATION_SETUP.md`.
- **2026-06-26 (c)** — Phase 3 built (WINCO): `KNOWLEDGE_ROLE` switch, `knowledge_core` (FedAvg in
  raw space + HMAC signing, unit-tested), `knowledge_aggregator` (gate+FedAvg+sign+publish),
  `knowledge_sync` (node push/pull/champion-compare), `ModelSubmission` + migration
  `b2e4submission02`, role-branched endpoints/scheduler. Added §3.3 production topology + role table.
  App unchanged (still pulls `/model/current`). Verified by sim; not run on live DBs.
- **2026-06-26 (b)** — Phase 2 built (WINCO side): `RiskModel` registry + merge migration
  `a9d1riskmodel01`, `services/risk_training.py` (numpy LR, feat-v1, 5-fold CV per facility),
  `routes/knowledge_api.py` (`/api/knowledge` + nightly scheduler), `app.py` wiring; app-side pull
  (`WincoModelPacket`, `getRiskModel`, ModelValidation "Pull from WINCO"). Python py_compile+import
  verified; app assembleDebug SUCCESSFUL. Awaiting live WINCO DB run (`flask db upgrade` + train).
- **2026-06-26** — Phase 1 built (in-app learned-model adoption + 5-fold CV + guardrail). Shared
  `RiskFeatures` for train/serve parity. Learned model drives forecast decision with heuristic
  fallback. Compile-verified; awaiting device validation on real data.
- **2026-06-25 (c)** — D5 resolved: multi-facility WINCO nodes + separate central aggregator; unit of
  knowledge = facility. D6 resolved: federation key = DATIM code (else salted instance+source hash).
- **2026-06-25 (b)** — WINCO reviewed (§8A): Flask/SQLAlchemy/Postgres, Bearer token + `_api_guard`,
  facility-scoped, scheduler present, pharmacy history server-side. D2 resolved (train on WINCO).
  New open D5 (deployment shape) + R5 (secrets). WINCO build plan drafted.
- **2026-06-25** — Document created. Phase 0 complete (on-device AI module, AI-controlled reminders,
  audit/measurement loop, validation + logistic-regression learning). Federated architecture
  designed; awaiting WINCO directory/DB review to begin Phase 2+.
