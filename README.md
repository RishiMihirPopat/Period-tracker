# Cycle — Private, On-Device Menstrual Cycle Tracker

**Cycle** is an offline-first, privacy-focused menstrual cycle and period tracking application for Android. Built entirely with Kotlin, Jetpack Compose, and Material Design 3, Cycle ensures all sensitive reproductive health data remains strictly encrypted on-device with zero telemetry, zero user accounts, and zero cloud dependencies.

---

## Key Features

### 100% Privacy & On-Device Security
- **Local SQLite / Room Database**: Sensitive health entries are persisted locally using modern Android Room architecture.
- **Zero Third-Party Trackers**: No analytics, telemetry, remote advertising SDKs, or cloud synchronization.
- **Encrypted Local Backup & Restore**: Export and import complete historical records in secure, user-managed backup files.
- **Doctor / Clinical Report Generator**: Export structured clinical summaries in PDF and Markdown formats to share directly with healthcare providers.

### Scientific & Statistical Prediction Engine
- **Bayesian Shrinkage Model**: Smooths personal cycle variations using clinical population priors ($28 \pm 3.5$ days) for new users, progressively weighting personal history as cycle records accumulate.
- **Multinomial & Cumulative Logit Modeling**: Predicts period onset ranges, fertile windows, and ovulation timing with explicit confidence intervals.
- **Phase Detection**: Categorizes the four biological phases:
  - **Menstrual Phase**
  - **Follicular Phase**
  - **Ovulatory Phase** (with 6-day fertile window)
  - **Luteal Phase**
- **Dynamic Adherence & Confidence Scoring**: Dynamically adjusts prediction intervals based on logging consistency and cycle regularity.

### Comprehensive Daily Symptom & Biomarker Logging
- **Menstrual Flow**: None, Spotting, Light, Medium, Heavy.
- **Physical Symptoms & Pain**: Cramps, headache, bloating, breast tenderness, fatigue, backache, acne, and custom tags with severity levels (Mild, Moderate, Severe).
- **Mood & Energy**: Comprehensive mood states and multi-point energy level tracking.
- **Fertility & Biomarkers**: Basal Body Temperature (BBT), Cervical Mucus characteristics, Ovulation Predictor Kits (LH test strips), and Pregnancy tests.
- **Intimacy & Protection**: Safe and private tracking of sexual activity and contraceptive methods.
- **Notes & Journaling**: Freeform notes for personal context.

### Visual Calendar & Day Details
- Month-by-month calendar view with dynamic color-coded phase dots and bleeding indicators.
- Quick day selector with interactive day-detail bottom sheets.
- "What to Expect" daily insights engine analyzing past cycle trends to provide timely symptom and energy expectations.

### Samsung One UI 8 Style Home Screen Widget
- **Adaptive Layout**: Supports both compact (small grid) and expanded (multi-column) widget form factors.
- **One UI 8 Aesthetics**: Styled with smooth rounded corners (`28dp`), semi-transparent surfaces, dynamic phase pill badges, and status indicator dots.
- **Instant Glanceability**: Displays current cycle day, active phase, days until next milestone, and quick action buttons for single-tap logging.
- **Android 12+ Responsive RemoteViews**: Responsive layout sizing with instant fallback rendering.

### Notifications & DST-Safe Reminders
- Gentle notifications for upcoming periods, expected ovulation, and late cycle reminders.
- **DST-Safe Scheduling**: Built with Java Time APIs to preserve exact 9:00 AM wall-clock trigger times across Daylight Saving Time (spring forward / fall back) transitions.

---

## Date-Math & Boundary Hardening

Cycle contains dedicated, automated test suites verifying complex temporal edge cases:
- **Leap Years**: Handles cycles starting on or spanning February 29th, leap year vs. common year offsets, and century leap-year rules.
- **Month Boundaries**: Seamless monotonic countdowns and predictions across 28, 29, 30, and 31-day months.
- **Year Transitions**: Clean state resolution for cycles crossing December 31st into January 1st without integer wrap bugs.
- **Daylight Saving Time (DST)**: Deterministic calendar math across 23-hour (spring forward) and 25-hour (fall back) transitions across US, European, and Southern Hemisphere timezones.

---

## Architecture & Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 (M3)
- **Language**: Kotlin 2.0+ (100% Kotlin codebase)
- **State Management**: MVVM with `ViewModel`, Kotlin Coroutines, and `StateFlow` / `collectAsStateWithLifecycle`
- **Navigation**: Jetpack Navigation Compose with type-safe `@Serializable` destination routes
- **Persistence**: Android Jetpack [Room Database](https://developer.android.com/training/data-storage/room)
- **Widgets**: Android AppWidget framework (`AppWidgetProvider` + `RemoteViews`)
- **Testing**:
  - [Robolectric](https://robolectric.org/) for local JVM unit and integration tests
  - [Roborazzi](https://github.com/takahirom/roborazzi) for visual screenshot testing
  - Comprehensive statistical and date-math unit tests

---

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/
│   │   ├── MainActivity.kt               # Single-activity container with Compose navigation
│   │   ├── data/
│   │   │   ├── backup/                   # Encrypted backup export and import logic
│   │   │   ├── local/                    # Room Database, DAOs, entities, and type converters
│   │   │   ├── model/                    # Domain models, enums (Flow, Symptom, Mood, Energy)
│   │   │   ├── report/                   # Doctor report generator (PDF & Markdown)
│   │   │   └── repository/               # CycleRepository for data access and prediction caching
│   │   ├── domain/
│   │   │   ├── CyclePredictionEngine.kt  # Core Bayesian cycle prediction and phase engine
│   │   │   ├── StatisticalPredictionEngine.kt # Advanced statistical model & uncertainty bands
│   │   │   ├── CycleStatsEngine.kt       # Cycle history, variation, and regularity scoring
│   │   │   └── WhatToExpectEngine.kt     # Historical pattern matching & daily insights
│   │   ├── ui/
│   │   │   ├── MainViewModel.kt          # Primary application state holder
│   │   │   ├── MainAppNavigation.kt      # Compose navigation graph and bottom bar
│   │   │   ├── screens/                  # Today, Calendar, Insights, Settings screens
│   │   │   └── theme/                    # Material 3 typography, shapes, and color schemes
│   │   ├── util/
│   │   │   └── CycleNotificationReceiver.kt # Notification manager & exact alarm scheduler
│   │   └── widget/
│   │       ├── CycleWidgetProvider.kt    # One UI 8 style home screen widget
│   │       └── CycleWidgetDataResolver.kt # Background data aggregator for widget rendering
│   └── res/
│       ├── layout/                       # RemoteViews widget layout XMLs
│       ├── xml/                          # AppWidgetProviderInfo configuration
│       └── values/                       # Strings, colors, styles
└── src/test/
    └── java/com/example/
        ├── domain/
        │   ├── CyclePredictionEngineTest.kt
        │   ├── CycleStatsEngineTest.kt
        │   ├── DateMathEdgeCasesTest.kt  # Leap years, DST, month/year boundaries
        │   └── WhatToExpectEngineTest.kt
        └── widget/
            └── CycleWidgetDataResolverTest.kt # Widget data resolution & edge cases
```

---

## Building & Testing

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17 or higher
- Android SDK with API 34+

### Build the Project
```bash
gradle :app:assembleDebug
```

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

---

## License & Privacy Notice
All user cycle data remains exclusively on the physical device. Cycle does not transmit health metrics to external servers.
