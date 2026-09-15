package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import com.example.domain.CumulativeLogitModel
import com.example.domain.StatisticalPredictionEngine
import com.example.domain.WhatToExpectEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cycle", appName)
  }

  @Test
  fun `cumulative logit model works with 5 categories`() {
    val model = CumulativeLogitModel(numCategories = 5)
    // Generate synthetic ordinal data
    val data = mutableListOf<Pair<Double, Int>>()
    for (i in 0 until 40) {
      val x = i.toDouble() / 40.0
      val y = when {
        x < 0.2 -> 0
        x < 0.4 -> 1
        x < 0.6 -> 2
        x < 0.8 -> 3
        else -> 4
      }
      data.add(x to y)
    }
    val fit = model.fit(data)
    assertNotNull(fit)
    val probs = fit!!.predictProbabilities(0.5)
    assertEquals(5, probs.size)
    assertEquals(1.0, probs.sum(), 0.001)
  }

  @Test
  fun `statistical prediction engine computes 5 level symptom predictions`() {
    val engine = StatisticalPredictionEngine()
    val today = LocalDate.now()
    val cycles = listOf(
      CycleEntity(id = 1, startDate = today.minusDays(84), endDate = today.minusDays(57), cycleLengthDays = 28, periodLengthDays = 5),
      CycleEntity(id = 2, startDate = today.minusDays(56), endDate = today.minusDays(29), cycleLengthDays = 28, periodLengthDays = 5),
      CycleEntity(id = 3, startDate = today.minusDays(28), endDate = today.minusDays(1), cycleLengthDays = 28, periodLengthDays = 5)
    )
    val logs = mutableListOf<DailyLogWithSymptoms>()

    // Simulate 45 logs across cycles with 5-level symptom severities
    for (i in 1..45) {
      val date = today.minusDays(i.toLong())
      logs.add(
        DailyLogWithSymptoms(
          log = DailyLogEntity(date = date),
          symptoms = listOf(
            SymptomLogEntity(
              dailyLogId = date,
              symptomTag = "cramps",
              severityLevel = 4 // Severe on 1..5 scale
            )
          )
        )
      )
    }

    val predictions = engine.computePredictions(
      today = today,
      currentCycleDay = 2,
      predictedCycleLength = 28,
      cycles = cycles,
      allLogs = logs
    )

    assertNotNull(predictions)
    val crampsPred = predictions!!.symptoms.firstOrNull { it.symptomTag == "cramps" }
    assertNotNull(crampsPred)
    assertNotNull(crampsPred?.severitySummary)
    assertTrue(crampsPred!!.severitySummary!!.contains("severe"))
  }

  @Test
  fun `user settings entity has phaseChangeAlertEnabled default true`() {
    val settings = com.example.data.local.entity.UserSettingsEntity()
    assertTrue(settings.phaseChangeAlertEnabled)
    val disabled = settings.copy(phaseChangeAlertEnabled = false)
    assertEquals(false, disabled.phaseChangeAlertEnabled)
  }

  @Test
  fun `main view model can be initialized without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = com.example.data.local.CycleDatabase.getInstance(context)
    val repository = com.example.data.repository.CycleRepository(
      dailyLogDao = database.dailyLogDao(),
      symptomLogDao = database.symptomLogDao(),
      cycleDao = database.cycleDao(),
      userSettingsDao = database.userSettingsDao(),
      profileDao = database.profileDao(),
      predictionEngine = com.example.domain.CyclePredictionEngine()
    )
    val vm = com.example.ui.MainViewModel(repository, context)
    assertNotNull(vm)
    assertNotNull(vm.userSettings.value)
    assertNotNull(vm.currentScreen.value)
  }
}

