package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CyclePredictionEngineTest {

    private val engine = CyclePredictionEngine()

    @Test
    fun `returns null predicted cycle length for 0 or 1 cycle`() {
        assertNull(engine.calculatePredictedCycleLength(emptyList()))
        assertNull(engine.calculatePredictedCycleLength(listOf(28)))
    }

    @Test
    fun `calculates empirical bayes shrinkage toward Bull et al population prior`() {
        // Population prior: Mean = 29.3, k = 3.5
        // 2 cycles with personal mean 29: (2 * 29 + 3.5 * 29.3) / 5.5 = (58 + 102.55) / 5.5 = 160.55 / 5.5 = 29.19 -> 29
        assertEquals(29, engine.calculatePredictedCycleLength(listOf(28, 30)))

        // Cold start calculation on 1 cycle: posterior exists even when gated
        val posteriorSingle = engine.calculatePosteriorCycleLength(listOf(35))
        // (1 * 35 + 3.5 * 29.3) / 4.5 = (35 + 102.55) / 4.5 = 30.56 -> shrank 35 down towards population prior
        assertTrue(posteriorSingle < 31.0 && posteriorSingle > 30.0)

        // As personal data accumulates (e.g. 10 cycles of 32d), prior fades out:
        val posteriorTen = engine.calculatePosteriorCycleLength(List(10) { 32 })
        // (10 * 32 + 3.5 * 29.3) / 13.5 = (320 + 102.55) / 13.5 = 31.3 -> closer to 32
        assertTrue(posteriorTen > 31.1)
    }

    @Test
    fun `robust MAD outlier detection down-weights extreme cycles`() {
        // Regular cycles around 28-29 days, with an anomalous 55-day cycle (illness/travel)
        val lengthsWithOutlier = listOf(28, 29, 28, 55, 29)
        val stats = engine.calculateRobustCycleStats(lengthsWithOutlier)

        // MAD should flag the 55-day cycle as an outlier
        assertTrue(stats.outlierCount >= 1)
        // Robust mean should remain close to 28-29 rather than pulled up to (169/5 = 33.8)
        assertTrue(stats.robustMean < 30.5)

        val predicted = engine.calculatePredictedCycleLength(lengthsWithOutlier)
        // Expected predicted cycle length should remain around 29 days
        assertEquals(29, predicted)
    }

    @Test
    fun `tracking gap in transition window widens confidence range`() {
        val today = LocalDate.of(2026, 9, 20)
        val cycleStart = today.minusDays(18)
        val periodStart = cycleStart.plusDays(29)
        val ovulDate = periodStart.minusDays(14)

        // User logged only 3 days in the past 18 days, missing ovulation transition
        val sparseLogs = setOf(cycleStart, cycleStart.plusDays(1), cycleStart.plusDays(2))
        val adherenceSparse = engine.calculateAdherenceMetrics(
            allLoggedDates = sparseLogs,
            currentCycleStartDate = cycleStart,
            today = today,
            completedCyclesCount = 3,
            predictedPeriodStart = periodStart,
            estimatedOvulationDate = ovulDate
        )

        assertTrue(adherenceSparse.isRangeWidened)
        assertTrue(adherenceSparse.confidenceMarginDays >= 4)

        // Fully logged stretch: 18 consecutive days logged
        val fullLogs = (0..18).map { cycleStart.plusDays(it.toLong()) }.toSet()
        val adherenceFull = engine.calculateAdherenceMetrics(
            allLoggedDates = fullLogs,
            currentCycleStartDate = cycleStart,
            today = today,
            completedCyclesCount = 3,
            predictedPeriodStart = periodStart,
            estimatedOvulationDate = ovulDate
        )

        assertFalse(adherenceFull.isRangeWidened)
        assertEquals(2, adherenceFull.confidenceMarginDays)
    }

    @Test
    fun `stable symptom marker performs soft Bayesian adjustment to luteal phase`() {
        val today = LocalDate.of(2026, 9, 20)
        val cycleStart = today.minusDays(20)

        // Symptom marker observed on day 17 implying luteal phase of 11 days
        val stableMarker = StableSymptomPhaseMarker(
            symptomTag = "breast_tenderness",
            meanDaysBeforePeriod = 3.0,
            standardDeviation = 0.8,
            cycleConsistencyRate = 0.8,
            isObservedInCurrentCycle = true,
            currentCycleDayObserved = 22,
            impliedLutealPhaseDays = 11.0
        )

        val adjustedLuteal = engine.calculatePosteriorLutealPhase(stableMarker)
        // Population luteal prior is 12.4 days (k=3.5), marker weight is 1.5
        // (3.5 * 12.4 + 1.5 * 11.0) / 5.0 = (43.4 + 16.5) / 5.0 = 59.9 / 5.0 = 11.98 days
        assertTrue(adjustedLuteal < 12.4 && adjustedLuteal > 11.8)

        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = listOf(28, 29, 28, 29),
            historicalPeriodDurations = listOf(5, 5, 5, 5),
            isCurrentlyBleeding = false,
            stableSymptomMarker = stableMarker
        )

        assertTrue(state is CyclePredictionState.Predicted)
        val predicted = state as CyclePredictionState.Predicted
        assertTrue(predicted.hasSymptomLutealAdjustment)
        assertEquals("breast_tenderness", predicted.symptomAdjustmentName)
    }

    @Test
    fun `cold start produces LearningCycle state with population prior`() {
        val today = LocalDate.of(2026, 9, 13)
        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = today.minusDays(3),
            completedCycleLengths = listOf(28), // Only 1 completed cycle
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = true
        )

        assertTrue(state is CyclePredictionState.LearningCycle)
        val learning = state as CyclePredictionState.LearningCycle
        assertEquals(4, learning.currentCycleDay)
        assertTrue(learning.isActivelyBleeding)
        assertTrue(learning.posteriorCycleLength > 28.0)
    }

    @Test
    fun `baseline with no active cycle produces AwaitingNextCycle state`() {
        val today = LocalDate.of(2026, 9, 13)
        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = null,
            completedCycleLengths = listOf(28, 30),
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false,
            lastCycleEndDate = today.minusDays(10)
        )

        assertTrue(state is CyclePredictionState.AwaitingNextCycle)
        val awaiting = state as CyclePredictionState.AwaitingNextCycle
        assertEquals(29, awaiting.averageCycleLength)
    }

    @Test
    fun `late period does not show negative days and produces Late status`() {
        val today = LocalDate.of(2026, 9, 13)
        // Cycle started 32 days ago; with 2x 28d cycles, Bayesian shrinkage toward Marquette prior (28.9)
        // yields predicted length of 29 days -> 32 - 29 = 3 days late
        val startDate = today.minusDays(32)
        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = startDate,
            completedCycleLengths = listOf(28, 28),
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false
        )

        assertTrue(state is CyclePredictionState.Predicted)
        val predicted = state as CyclePredictionState.Predicted
        assertEquals(29, predicted.predictedCycleLength)
        assertTrue(predicted.timingStatus is PeriodTimingStatus.Late)
        val late = predicted.timingStatus as PeriodTimingStatus.Late
        assertEquals(3L, late.daysLate)
    }

    @Test
    fun `upcoming period produces Upcoming status`() {
        val today = LocalDate.of(2026, 9, 13)
        // Cycle started 20 days ago; with 2x 28d cycles, Bayesian posterior is 29 days -> 29 - 20 = 9 days remaining
        val startDate = today.minusDays(20)
        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = startDate,
            completedCycleLengths = listOf(28, 28),
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false
        )

        assertTrue(state is CyclePredictionState.Predicted)
        val predicted = state as CyclePredictionState.Predicted
        assertEquals(29, predicted.predictedCycleLength)
        assertTrue(predicted.timingStatus is PeriodTimingStatus.Upcoming)
        val upcoming = predicted.timingStatus as PeriodTimingStatus.Upcoming
        assertEquals(9L, upcoming.daysRemaining)
    }
}
