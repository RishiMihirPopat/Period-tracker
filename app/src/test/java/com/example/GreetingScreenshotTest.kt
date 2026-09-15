package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.model.FlowIntensity
import com.example.domain.CyclePhase
import com.example.domain.CyclePredictionState
import com.example.domain.FertileWindow
import com.example.domain.PeriodTimingStatus
import com.example.ui.components.CycleRing
import com.example.ui.screens.CalendarScreen
import com.example.ui.theme.CycleTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

import com.example.domain.WhatToExpectEngine
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.SettingsScreen
import com.example.domain.CycleStats

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val whatToExpectEngine = WhatToExpectEngine()

  @Test
  fun greeting_screenshot() {
    val today = LocalDate.of(2026, 9, 13)
    val state = CyclePredictionState.Predicted(
        currentCycleDay = 14,
        predictedCycleLength = 28,
        predictedPeriodStart = today.plusDays(14),
        predictedPeriodEnd = today.plusDays(18),
        estimatedOvulationDate = today,
        fertileWindow = FertileWindow(today.minusDays(5), today.plusDays(1)),
        currentPhase = CyclePhase.OVULATORY,
        timingStatus = PeriodTimingStatus.Upcoming(14)
    )

    composeTestRule.setContent {
      CycleTheme {
        CycleRing(predictionState = state)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun today_follicular_screenshot() {
    val today = LocalDate.of(2026, 9, 14)
    val state = CyclePredictionState.Predicted(
        currentCycleDay = 8,
        predictedCycleLength = 28,
        predictedPeriodStart = today.plusDays(20),
        predictedPeriodEnd = today.plusDays(24),
        estimatedOvulationDate = today.plusDays(6),
        fertileWindow = FertileWindow(today.plusDays(1), today.plusDays(7)),
        currentPhase = CyclePhase.FOLLICULAR,
        timingStatus = PeriodTimingStatus.Upcoming(20),
        currentCycleStartDate = today.minusDays(7)
    )
    val expectation = whatToExpectEngine.getExpectation(
        phase = CyclePhase.FOLLICULAR,
        cycleDay = 8
    )

    composeTestRule.setContent {
      CycleTheme {
        TodayScreen(
            predictionState = state,
            expectation = expectation,
            todayLog = null,
            onOpenLog = {},
            onOpenSettings = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/today_follicular.png")
  }

  @Test
  fun today_ovulatory_screenshot() {
    val today = LocalDate.of(2026, 9, 14)
    val state = CyclePredictionState.Predicted(
        currentCycleDay = 14,
        predictedCycleLength = 28,
        predictedPeriodStart = today.plusDays(14),
        predictedPeriodEnd = today.plusDays(18),
        estimatedOvulationDate = today,
        fertileWindow = FertileWindow(today.minusDays(5), today.plusDays(1)),
        currentPhase = CyclePhase.OVULATORY,
        timingStatus = PeriodTimingStatus.Upcoming(14),
        currentCycleStartDate = today.minusDays(13)
    )
    val expectation = whatToExpectEngine.getExpectation(
        phase = CyclePhase.OVULATORY,
        cycleDay = 14
    )

    composeTestRule.setContent {
      CycleTheme {
        TodayScreen(
            predictionState = state,
            expectation = expectation,
            todayLog = null,
            onOpenLog = {},
            onOpenSettings = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/today_ovulatory.png")
  }

  @Test
  fun settings_screenshot() {
    composeTestRule.setContent {
      CycleTheme {
        SettingsScreen(
            settings = com.example.data.local.entity.UserSettingsEntity(),
            onUpdateLutealPhase = {},
            onToggleNotifications = {},
            onToggleFertileAlert = {},
            onToggleDailyDigest = {},
            onSetPin = {},
            onBack = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/settings.png")
  }

  @Test
  fun calendar_screenshot() {
    val selectedDate = LocalDate.of(2026, 9, 14)
    val cycleStartDate = LocalDate.of(2026, 8, 25)
    val state = CyclePredictionState.Predicted(
        currentCycleDay = 21,
        predictedCycleLength = 28,
        predictedPeriodStart = LocalDate.of(2026, 9, 22),
        predictedPeriodEnd = LocalDate.of(2026, 9, 26),
        estimatedOvulationDate = LocalDate.of(2026, 9, 7),
        fertileWindow = FertileWindow(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 8)),
        currentPhase = CyclePhase.LUTEAL,
        timingStatus = PeriodTimingStatus.Upcoming(8)
    )

    val sampleLogs = listOf(
        // Period bleeding days
        DailyLogWithSymptoms(
            log = DailyLogEntity(date = LocalDate.of(2026, 8, 25), flowIntensity = FlowIntensity.HEAVY),
            symptoms = listOf(SymptomLogEntity(dailyLogId = LocalDate.of(2026, 8, 25), symptomTag = "cramps", severityLevel = 2))
        ),
        // Days with logged symptom/mood but NO flow (to verify small dot indicator)
        DailyLogWithSymptoms(
            log = DailyLogEntity(date = LocalDate.of(2026, 9, 10), flowIntensity = FlowIntensity.NONE, mood = listOf("calm")),
            symptoms = listOf(SymptomLogEntity(dailyLogId = LocalDate.of(2026, 9, 10), symptomTag = "bloating", severityLevel = 2))
        ),
        DailyLogWithSymptoms(
            log = DailyLogEntity(date = LocalDate.of(2026, 9, 12), flowIntensity = FlowIntensity.NONE, mood = listOf("sensitive")),
            symptoms = listOf(SymptomLogEntity(dailyLogId = LocalDate.of(2026, 9, 12), symptomTag = "cramps", severityLevel = 1))
        )
    )

    val cycles = listOf(
        CycleEntity(
            startDate = cycleStartDate,
            endDate = null,
            periodLengthDays = 5,
            cycleLengthDays = null
        )
    )

    composeTestRule.setContent {
      CycleTheme {
        CalendarScreen(
            currentMonth = YearMonth.of(2026, 9),
            selectedDate = selectedDate,
            predictionState = state,
            monthLogs = sampleLogs,
            cycles = cycles,
            onDayClick = {},
            onPrevMonth = {},
            onNextMonth = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/calendar.png")
  }
}
