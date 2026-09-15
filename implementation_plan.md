# Implementation Plan: Private On-Device Period & Cycle Tracker (Phase 1)
*Aligned with @period-tracker-app-plan.md*

## 0. Non-Negotiable Privacy Architecture
- **Zero Network**: No `android.permission.INTERNET` declared in `AndroidManifest.xml`.
- **Zero External Telemetry**: No analytics, no crashlytics, no network libraries (`Retrofit`, `OkHttp`, `Firebase` removed).
- **At-Rest Encryption**: AndroidX Room database encrypted with SQLCipher (`net.zetetic:sqlcipher-android`) using a 256-bit passphrase secured by the Android KeyStore.

---

## 1. Divergence Analysis & Corrections (vs. Sections 2 & 3)

### Divergence 1: Cold-Start Handling (Section 3) — **FIXED**
- **Original draft in implementation_plan.md**: Stated that if fewer cycles exist, it would silently fall back to a default 28-day prediction.
- **Correction per Section 3**: If fewer than 2 full cycles have been logged:
  - **No predicted date or false-precision period date will be shown**.
  - Explicitly show: **"Learning your cycle — log a couple more periods for predictions."**
  - The cycle day counter (e.g., "Day 12 of cycle") continues to accurately count from the most recent period start.
  - Phase estimation displays "Menstrual Phase" during active flow, and "Learning Cycle" / phase guide until 2 full cycles establish her personal baseline.

### Divergence 2: Data Model Schema Alignment (Section 2) — **FIXED**
- Added nullable fields to `DailyLogEntity` now so future phases require zero database migration:
  - `cycle_id` (Long?)
  - `bbt_celsius` (Double?)
  - `sexual_activity` (String?: `NONE`, `PROTECTED`, `UNPROTECTED`)
  - `medication_taken` (Boolean?)
- `CycleEntity`: `id`, `start_date`, `end_date`, `period_length_days`.
- Pre-seeded + user-extensible symptom tags (`cramps`, `headache`, `bloating`, `acne`, `breast_tenderness`, `fatigue`, `nausea`, `back_pain`) and mood tags (`happy`, `sad`, `anxious`, `irritable`, `calm`, `energetic`, `low_energy`, `sensitive`).

### Divergence 3: Weighted Average Calculation (Section 3)
- For users with $\ge 2$ completed cycles:
  - Predicted cycle length uses the Section 3 formula: weighted average of the last up to 6 cycles (weighting the most recent 3 cycles $2\times$ the older 3).
  - Estimated ovulation = `predictedPeriodStart - lutealPhaseLength` (default 14).
  - Fertile window = ovulation day $-5$ to $+1$.

---

## 2. Verification Status
- `compile_applet` was executed and successfully resolved: **Gradle build compiles cleanly**.

---

## 3. Phase 1 Implementation Steps (Awaiting Approval)

### Step 1: Zero-Network Project Hardening
- Remove unused template dependencies (`firebase.bom`, `firebase.ai`, `retrofit`, `okhttp`, `logging-interceptor`) in `app/build.gradle.kts`.
- Add `net.zetetic:sqlcipher-android` for SQLCipher encryption.
- Ensure `AndroidManifest.xml` retains zero `INTERNET` permission.

### Step 2: Encrypted Room Database (Section 2)
- `DatabaseKeyManager`: Generates and manages the 256-bit passphrase via Android KeyStore.
- `CycleDatabase`: Encrypted via SQLCipher `SupportOpenHelperFactory`.
- Entities & DAOs: `DailyLogDao`, `CycleDao`, `UserSettingsDao`.

### Step 3: Pure Kotlin Cycle Prediction & Cold-Start Engine (Section 3)
- Implements weighted average of last 6 cycles (recent 3 at $2\times$ weight).
- Implements strict `< 2` completed cycles cold-start handling ("Learning your cycle...").
- Computes Cycle Day counter, current cycle phase, and fertile window when $\ge 2$ cycles exist.

### Step 4: UI Development (Jetpack Compose Material 3)
- **Today Screen**:
  - Hero Cycle Day counter ("Day X of cycle").
  - Cold-start learning banner (if $< 2$ cycles) OR Predicted period & phase banner (if $\ge 2$ cycles).
  - Today's logged flow & symptoms summary + quick log button.
- **Log Entry Screen**:
  - Date navigator.
  - Flow intensity selector (`None`, `Spotting`, `Light`, `Medium`, `Heavy`).
  - Multi-select Symptom chips.
  - Multi-select Mood chips.
  - Notes field.
- **Calendar Screen**:
  - Month grid view.
  - Visual indicators for logged flow, predicted window (when available), and symptom indicators.
  - Tap date to view or edit log.
- **Bottom Navigation**: Seamlessly navigate between Today, Calendar, and Log.

### Step 5: Unit Tests & Build Verification
- Unit test suite verifying cold-start status, weighted average cycle prediction, and cycle day counting.
- `compile_applet` build pass.
