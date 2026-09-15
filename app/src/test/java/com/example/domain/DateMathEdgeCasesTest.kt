package com.example.domain

import com.example.data.local.converter.Converters
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Explicit test coverage for date-math edge cases:
 * - Leap years (February 29, leap day cycle starts, Feb 1 in leap vs non-leap, century leap years)
 * - Month boundaries (30-day, 31-day, 28-day months, month-end cycle starts, monotonic countdown)
 * - Year boundaries (December to January cycles, New Year transitions, multi-year stats)
 * - Daylight Saving Time (DST) transitions (US spring forward 23h, US fall back 25h, European DST,
 *   Southern Hemisphere DST, wall-clock notification trigger accuracy across DST transitions)
 * - ISO converter serialization round-trips for leap days and year boundaries
 */
class DateMathEdgeCasesTest {

    private val engine = CyclePredictionEngine()
    private val statsEngine = CycleStatsEngine()
    private val expectEngine = WhatToExpectEngine()
    private val converters = Converters()

    // =========================================================================
    // 1. LEAP YEAR EDGE CASES
    // =========================================================================

    @Test
    fun `cycle starting directly on leap day February 29 2024 calculates correct day and predictions`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val today = LocalDate.of(2024, 3, 1) // Day after leap day

        val state = engine.calculatePrediction(
            today = today,
            currentCycleStartDate = leapDay,
            completedCycleLengths = listOf(28, 28),
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false
        )

        assertTrue(state is CyclePredictionState.Predicted)
        val predicted = state as CyclePredictionState.Predicted

        // Day after leap day is Day 2 (leap day was Day 1)
        assertEquals(2, predicted.currentCycleDay)
        // Bayesian shrinkage with 2x 28d cycles yields 29d predicted length
        assertEquals(29, predicted.predictedCycleLength)

        // Leap day + 29 days = 2024-03-29
        assertEquals(LocalDate.of(2024, 3, 29), predicted.predictedPeriodStart)
        // 5-day period starting March 29 ends April 2 (spans March to April boundary!)
        assertEquals(LocalDate.of(2024, 4, 2), predicted.predictedPeriodEnd)

        // Estimated ovulation: March 29 - 12 days = March 17
        assertEquals(LocalDate.of(2024, 3, 17), predicted.estimatedOvulationDate)
        // Fertile window: March 12 to March 18
        assertEquals(LocalDate.of(2024, 3, 12), predicted.fertileWindow.startDate)
        assertEquals(LocalDate.of(2024, 3, 18), predicted.fertileWindow.endDate)

        // On March 1, timing status: 28 days remaining until March 29
        assertTrue(predicted.timingStatus is PeriodTimingStatus.Upcoming)
        val upcoming = predicted.timingStatus as PeriodTimingStatus.Upcoming
        assertEquals(28L, upcoming.daysRemaining)
    }

    @Test
    fun `cycle spanning leap day has different predicted period start compared to non-leap year`() {
        // Cycle starting on Feb 15
        val leapStart = LocalDate.of(2024, 2, 15)
        val commonStart = LocalDate.of(2025, 2, 15)

        // In 2024 (leap year, 29 days in Feb), 28 days from Feb 15:
        // 14 days left in Feb (15 to 29) + 14 days in March = March 14
        val leapPredictedDate = leapStart.plusDays(28)
        assertEquals(LocalDate.of(2024, 3, 14), leapPredictedDate)

        // In 2025 (common year, 28 days in Feb), 28 days from Feb 15:
        // 13 days left in Feb (15 to 28) + 15 days in March = March 15 (1 calendar day later!)
        val commonPredictedDate = commonStart.plusDays(28)
        assertEquals(LocalDate.of(2025, 3, 15), commonPredictedDate)

        // Verify with the prediction engine
        val leapPrediction = engine.calculatePrediction(
            today = leapStart.plusDays(10), // Feb 25, 2024
            currentCycleStartDate = leapStart,
            completedCycleLengths = List(10) { 28 },
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(LocalDate.of(2024, 3, 14), leapPrediction.predictedPeriodStart)

        val commonPrediction = engine.calculatePrediction(
            today = commonStart.plusDays(10), // Feb 25, 2025
            currentCycleStartDate = commonStart,
            completedCycleLengths = List(10) { 28 },
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(LocalDate.of(2025, 3, 15), commonPrediction.predictedPeriodStart)
    }

    @Test
    fun `cycle starting on February 1 predicts period start on leap day in leap year versus March 1 in non-leap year`() {
        val leapFeb1 = LocalDate.of(2024, 2, 1)
        val commonFeb1 = LocalDate.of(2025, 2, 1)

        // With 28-day cycle, Feb 1 + 28 days:
        // 2024 (leap): Feb 1 + 28 days = Feb 29 (leap day!)
        val leapPeriodStart = leapFeb1.plusDays(28)
        assertEquals(LocalDate.of(2024, 2, 29), leapPeriodStart)

        // 2025 (non-leap): Feb 1 + 28 days = March 1
        val commonPeriodStart = commonFeb1.plusDays(28)
        assertEquals(LocalDate.of(2025, 3, 1), commonPeriodStart)

        // Test cycle phase on Feb 29, 2024:
        val phaseOnLeapDay = CyclePredictionEngine.determinePhaseForDate(
            date = LocalDate.of(2024, 2, 29),
            cycleStartDate = leapFeb1,
            cycleLength = 28,
            periodDuration = 5
        )
        // Day 29 of 28d cycle is day 1 of the next cycle -> Menstrual phase
        assertEquals(CyclePhase.MENSTRUAL, phaseOnLeapDay)

        // On Feb 28, 2024: Day 28 -> Luteal phase
        val phaseOnFeb28 = CyclePredictionEngine.determinePhaseForDate(
            date = LocalDate.of(2024, 2, 28),
            cycleStartDate = leapFeb1,
            cycleLength = 28,
            periodDuration = 5
        )
        assertEquals(CyclePhase.LUTEAL, phaseOnFeb28)
    }

    @Test
    fun `adherence metrics correctly count February 29 without skipping days`() {
        val cycleStart = LocalDate.of(2024, 2, 20)
        val today = LocalDate.of(2024, 3, 5) // 15-day span including Feb 29
        val periodStart = cycleStart.plusDays(28)
        val ovulDate = periodStart.minusDays(14)

        // Log every single day including Feb 29
        val loggedDates = (0..14).map { cycleStart.plusDays(it.toLong()) }.toSet()
        assertTrue(loggedDates.contains(LocalDate.of(2024, 2, 29)))

        val metrics = engine.calculateAdherenceMetrics(
            allLoggedDates = loggedDates,
            currentCycleStartDate = cycleStart,
            today = today,
            completedCyclesCount = 3,
            predictedPeriodStart = periodStart,
            estimatedOvulationDate = ovulDate
        )

        assertEquals(1.0, metrics.loggingDensity, 0.001)
        assertFalse(metrics.isRangeWidened)
        assertEquals(2, metrics.confidenceMarginDays)
    }

    @Test
    fun `century leap year rules handled accurately by temporal math`() {
        // Year 2000 is a leap year (divisible by 400)
        assertTrue(Year.of(2000).isLeap)
        val feb29_2000 = LocalDate.of(2000, 2, 29)
        assertEquals(1, ChronoUnit.DAYS.between(feb29_2000, LocalDate.of(2000, 3, 1)))

        // Year 1900 and 2100 are NOT leap years (divisible by 100 but not 400)
        assertFalse(Year.of(1900).isLeap)
        assertFalse(Year.of(2100).isLeap)
        assertEquals(1, ChronoUnit.DAYS.between(LocalDate.of(1900, 2, 28), LocalDate.of(1900, 3, 1)))
        assertEquals(1, ChronoUnit.DAYS.between(LocalDate.of(2100, 2, 28), LocalDate.of(2100, 3, 1)))
    }

    // =========================================================================
    // 2. MONTH BOUNDARY CROSSING EDGE CASES
    // =========================================================================

    @Test
    fun `monotonic day countdown across month boundaries without skips or negative gaps`() {
        val cycleStart = LocalDate.of(2026, 4, 15) // April has 30 days
        val completedCycles = List(10) { 28 } // Predicted length = 28
        // Predicted period start: April 15 + 28 days = May 13

        val datesAndExpectedDaysRemaining = listOf(
            LocalDate.of(2026, 4, 29) to 14L,
            LocalDate.of(2026, 4, 30) to 13L, // Last day of April
            LocalDate.of(2026, 5, 1) to 12L,  // First day of May
            LocalDate.of(2026, 5, 2) to 11L,
            LocalDate.of(2026, 5, 11) to 2L,
            LocalDate.of(2026, 5, 12) to 1L
        )

        for ((checkDate, expectedRemaining) in datesAndExpectedDaysRemaining) {
            val state = engine.calculatePrediction(
                today = checkDate,
                currentCycleStartDate = cycleStart,
                completedCycleLengths = completedCycles,
                historicalPeriodDurations = listOf(5),
                isCurrentlyBleeding = false
            ) as CyclePredictionState.Predicted

            assertTrue("Expected Upcoming status on $checkDate", state.timingStatus is PeriodTimingStatus.Upcoming)
            val upcoming = state.timingStatus as PeriodTimingStatus.Upcoming
            assertEquals("Mismatch for $checkDate", expectedRemaining, upcoming.daysRemaining)
        }

        // On May 13 (target date): ExpectedToday
        val expectedTodayState = engine.calculatePrediction(
            today = LocalDate.of(2026, 5, 13),
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertTrue(expectedTodayState.timingStatus is PeriodTimingStatus.ExpectedToday)

        // On May 14 (1 day late) and May 16 (3 days late)
        val late1 = engine.calculatePrediction(
            today = LocalDate.of(2026, 5, 14),
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertTrue(late1.timingStatus is PeriodTimingStatus.Late)
        assertEquals(1L, (late1.timingStatus as PeriodTimingStatus.Late).daysLate)

        val late3 = engine.calculatePrediction(
            today = LocalDate.of(2026, 5, 16),
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertTrue(late3.timingStatus is PeriodTimingStatus.Late)
        assertEquals(3L, (late3.timingStatus as PeriodTimingStatus.Late).daysLate)
    }

    @Test
    fun `cycle starting on month-end 31st transitions seamlessly to next month`() {
        val startJan31 = LocalDate.of(2026, 1, 31)

        // Day 1 = Jan 31
        // Day 2 = Feb 1
        // Day 29 = Feb 28 (common year)
        // Day 30 = March 1
        val checkFeb1 = LocalDate.of(2026, 2, 1)
        val dayOnFeb1 = ChronoUnit.DAYS.between(startJan31, checkFeb1).toInt() + 1
        assertEquals(2, dayOnFeb1)

        val checkMar1 = LocalDate.of(2026, 3, 1)
        val dayOnMar1 = ChronoUnit.DAYS.between(startJan31, checkMar1).toInt() + 1
        assertEquals(30, dayOnMar1)

        val stateMar1 = engine.calculatePrediction(
            today = checkMar1,
            currentCycleStartDate = startJan31,
            completedCycleLengths = List(10) { 28 },
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted

        assertEquals(30, stateMar1.currentCycleDay)
        // Predicted period start was Jan 31 + 28 days = Feb 28
        assertEquals(LocalDate.of(2026, 2, 28), stateMar1.predictedPeriodStart)
        // On March 1, it is 1 day late (March 1 - Feb 28 = 1 day)
        assertTrue(stateMar1.timingStatus is PeriodTimingStatus.Late)
        assertEquals(1L, (stateMar1.timingStatus as PeriodTimingStatus.Late).daysLate)
    }

    @Test
    fun `consecutive 31-day months July and August calculate exact day differences`() {
        val jul31 = LocalDate.of(2026, 7, 31)
        val aug1 = LocalDate.of(2026, 8, 1)
        val aug31 = LocalDate.of(2026, 8, 31)
        val sep1 = LocalDate.of(2026, 9, 1)

        assertEquals(1, ChronoUnit.DAYS.between(jul31, aug1))
        assertEquals(31, ChronoUnit.DAYS.between(jul31, aug31))
        assertEquals(32, ChronoUnit.DAYS.between(jul31, sep1))
    }

    // =========================================================================
    // 3. YEAR BOUNDARY CROSSING EDGE CASES
    // =========================================================================

    @Test
    fun `cycle starting in late December and extending into January crosses year boundary cleanly`() {
        val cycleStart = LocalDate.of(2025, 12, 20)
        val completedCycles = listOf(29, 29, 29) // 29-day cycle

        // New Year's Eve: Dec 31, 2025
        val nye = LocalDate.of(2025, 12, 31)
        val stateNye = engine.calculatePrediction(
            today = nye,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted

        // Dec 20 to Dec 31 is 11 days between -> Day 12
        assertEquals(12, stateNye.currentCycleDay)
        assertEquals(LocalDate.of(2026, 1, 18), stateNye.predictedPeriodStart)
        assertEquals(18L, (stateNye.timingStatus as PeriodTimingStatus.Upcoming).daysRemaining)
        assertEquals(CyclePhase.FOLLICULAR, stateNye.currentPhase)

        // New Year's Day: Jan 1, 2026
        val nyd = LocalDate.of(2026, 1, 1)
        val stateNyd = engine.calculatePrediction(
            today = nyd,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5, 5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted

        // Dec 20 to Jan 1 is 12 days between -> Day 13
        assertEquals(13, stateNyd.currentCycleDay)
        assertEquals(LocalDate.of(2026, 1, 18), stateNyd.predictedPeriodStart)
        assertEquals(17L, (stateNyd.timingStatus as PeriodTimingStatus.Upcoming).daysRemaining)

        // Fertile window and ovulation in January:
        // Ovulation: Jan 18 - 12 days = Jan 6, 2026
        assertEquals(LocalDate.of(2026, 1, 6), stateNyd.estimatedOvulationDate)
        assertEquals(LocalDate.of(2026, 1, 1), stateNyd.fertileWindow.startDate)
        assertEquals(LocalDate.of(2026, 1, 7), stateNyd.fertileWindow.endDate)
        // On Jan 1, fertile window has started -> Ovulation phase
        assertEquals(CyclePhase.OVULATORY, stateNyd.currentPhase)
    }

    @Test
    fun `late period spanning year boundary does not produce negative numbers or wrap errors`() {
        val cycleStart = LocalDate.of(2025, 12, 1)
        val completedCycles = List(20) { 28 } // Strong personal history converges to 28-day predicted cycle

        // Check on Jan 3, 2026 (5 days late across New Year boundary)
        val checkDate = LocalDate.of(2026, 1, 3)
        val state = engine.calculatePrediction(
            today = checkDate,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted

        assertEquals(LocalDate.of(2025, 12, 29), state.predictedPeriodStart)
        assertTrue(state.timingStatus is PeriodTimingStatus.Late)
        val late = state.timingStatus as PeriodTimingStatus.Late
        // Dec 29, 2025 to Jan 3, 2026 is exactly 5 days
        assertEquals(5L, late.daysLate)
        // Cycle day: Dec 1 to Jan 3 is 33 days + 1 = 34
        assertEquals(34, state.currentCycleDay)
    }

    @Test
    fun `cycle stats engine handles multi-year historical cycles without skew`() {
        val cycles = listOf(
            CycleEntity(
                id = 1,
                startDate = LocalDate.of(2024, 12, 10),
                endDate = LocalDate.of(2025, 1, 7),
                periodLengthDays = 5,
                cycleLengthDays = 29
            ),
            CycleEntity(
                id = 2,
                startDate = LocalDate.of(2025, 1, 8),
                endDate = LocalDate.of(2025, 2, 5),
                periodLengthDays = 5,
                cycleLengthDays = 29
            ),
            CycleEntity(
                id = 3,
                startDate = LocalDate.of(2025, 12, 15),
                endDate = LocalDate.of(2026, 1, 12),
                periodLengthDays = 5,
                cycleLengthDays = 29
            )
        )

        val stats = statsEngine.calculateStats(cycles, emptyList())
        assertEquals(3, stats.totalCyclesTracked)
        assertEquals(29, stats.averageCycleLengthDays)
        assertEquals(5, stats.averagePeriodLengthDays)
        assertEquals(29, stats.shortestCycleDays)
        assertEquals(29, stats.longestCycleDays)
        assertEquals(0.0, stats.cycleLengthStandardDeviation!!, 0.001)
        assertTrue(stats.regularityScore.contains("High regularity"))
    }

    @Test
    fun `what to expect engine pattern matches across year boundary`() {
        val today = LocalDate.of(2026, 1, 10) // Day 15 of cycle starting Dec 27, 2025
        val cycleStart = LocalDate.of(2025, 12, 27)

        val historicalCycles = listOf(
            CycleEntity(
                id = 1,
                startDate = LocalDate.of(2025, 11, 28),
                endDate = LocalDate.of(2025, 12, 26),
                periodLengthDays = 5,
                cycleLengthDays = 29
            )
        )

        // Historical log on Day 15 of previous cycle: Nov 28 + 14 days = Dec 12, 2025
        val historicalLogs = listOf(
            DailyLogWithSymptoms(
                log = DailyLogEntity(
                    date = LocalDate.of(2025, 12, 12),
                    energyLevel = EnergyLevel.HIGH,
                    mood = listOf("energized")
                ),
                symptoms = listOf(
                    SymptomLogEntity(symptomTag = "bloating", severityLevel = 2)
                )
            ),
            DailyLogWithSymptoms(
                log = DailyLogEntity(
                    date = LocalDate.of(2025, 12, 13),
                    energyLevel = EnergyLevel.HIGH,
                    mood = listOf("energized")
                ),
                symptoms = listOf(
                    SymptomLogEntity(symptomTag = "bloating", severityLevel = 2)
                )
            )
        )

        val expectation = expectEngine.getExpectation(
            phase = CyclePhase.OVULATORY,
            cycleDay = 15,
            allLogs = historicalLogs,
            cycles = historicalCycles,
            today = today
        )

        assertNotNull(expectation.personalizedInsight)
        assertTrue(expectation.personalizedInsight!!.contains("bloating"))
    }

    // =========================================================================
    // 4. DAYLIGHT SAVING TIME (DST) TRANSITIONS
    // =========================================================================

    @Test
    fun `cycle spanning US spring-forward DST maintains exact day counts and date progression`() {
        // US Spring Forward 2026: Sunday, March 8, 2026 (clock jumps from 02:00 to 03:00, 23-hour day)
        val zoneId = ZoneId.of("America/New_York")
        val cycleStart = LocalDate.of(2026, 3, 1)
        val completedCycles = List(20) { 28 }

        // Day before DST (Saturday, March 7)
        val sat = LocalDate.of(2026, 3, 7)
        val stateSat = engine.calculatePrediction(
            today = sat,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(7, stateSat.currentCycleDay)

        // Day of DST (Sunday, March 8 - 23-hour day)
        val sun = LocalDate.of(2026, 3, 8)
        val stateSun = engine.calculatePrediction(
            today = sun,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        // Cycle day advances by exactly +1, not affected by the 23-hour duration
        assertEquals(8, stateSun.currentCycleDay)

        // Day after DST (Monday, March 9)
        val mon = LocalDate.of(2026, 3, 9)
        val stateMon = engine.calculatePrediction(
            today = mon,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(9, stateMon.currentCycleDay)

        // Predicted period start: March 1 + 28 days = March 29
        assertEquals(LocalDate.of(2026, 3, 29), stateSun.predictedPeriodStart)
        assertEquals(28, ChronoUnit.DAYS.between(cycleStart, stateSun.predictedPeriodStart).toInt())
    }

    @Test
    fun `cycle spanning US fall-back DST maintains exact day counts and date progression`() {
        // US Fall Back 2026: Sunday, November 1, 2026 (clock jumps from 02:00 back to 01:00, 25-hour day)
        val zoneId = ZoneId.of("America/New_York")
        val cycleStart = LocalDate.of(2026, 10, 18)
        val completedCycles = List(20) { 28 }

        // Day before fall-back (Saturday, Oct 31)
        val sat = LocalDate.of(2026, 10, 31)
        val stateSat = engine.calculatePrediction(
            today = sat,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(14, stateSat.currentCycleDay)

        // Day of fall-back (Sunday, Nov 1 - 25-hour day)
        val sun = LocalDate.of(2026, 11, 1)
        val stateSun = engine.calculatePrediction(
            today = sun,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(15, stateSun.currentCycleDay)

        // Day after fall-back (Monday, Nov 2)
        val mon = LocalDate.of(2026, 11, 2)
        val stateMon = engine.calculatePrediction(
            today = mon,
            currentCycleStartDate = cycleStart,
            completedCycleLengths = completedCycles,
            historicalPeriodDurations = listOf(5),
            isCurrentlyBleeding = false
        ) as CyclePredictionState.Predicted
        assertEquals(16, stateMon.currentCycleDay)

        // Predicted period start: Oct 18 + 28 days = Nov 15
        assertEquals(LocalDate.of(2026, 11, 15), stateSun.predictedPeriodStart)
        assertEquals(28, ChronoUnit.DAYS.between(cycleStart, stateSun.predictedPeriodStart).toInt())
    }

    @Test
    fun `notification scheduling produces correct 9 AM wall clock trigger across DST spring forward`() {
        val zone = ZoneId.of("America/New_York")

        // Saturday March 7, 2026 (EST, UTC-5)
        val satDate = LocalDate.of(2026, 3, 7)
        val satTrigger = satDate.atTime(9, 0).atZone(zone).toInstant()

        // Sunday March 8, 2026 (EDT, UTC-4 due to spring forward at 2am)
        val sunDate = LocalDate.of(2026, 3, 8)
        val sunTrigger = sunDate.atTime(9, 0).atZone(zone).toInstant()

        // Monday March 9, 2026 (EDT, UTC-4)
        val monDate = LocalDate.of(2026, 3, 9)
        val monTrigger = monDate.atTime(9, 0).atZone(zone).toInstant()

        // Across spring forward, the duration between 9am Sat and 9am Sun is exactly 23 HOURS
        val satToSunDuration = Duration.between(satTrigger, sunTrigger)
        assertEquals(23, satToSunDuration.toHours())

        // From Sun 9am to Mon 9am is standard 24 HOURS
        val sunToMonDuration = Duration.between(sunTrigger, monTrigger)
        assertEquals(24, sunToMonDuration.toHours())

        // Both triggers represent exactly 09:00:00 local wall clock time in their respective zones
        assertEquals(9, satDate.atTime(9, 0).atZone(zone).hour)
        assertEquals(9, sunDate.atTime(9, 0).atZone(zone).hour)
        assertEquals(9, monDate.atTime(9, 0).atZone(zone).hour)
    }

    @Test
    fun `notification scheduling produces correct 9 AM wall clock trigger across DST fall back`() {
        val zone = ZoneId.of("America/New_York")

        // Saturday Oct 31, 2026 (EDT, UTC-4)
        val satDate = LocalDate.of(2026, 10, 31)
        val satTrigger = satDate.atTime(9, 0).atZone(zone).toInstant()

        // Sunday Nov 1, 2026 (EST, UTC-5 due to fall back at 2am)
        val sunDate = LocalDate.of(2026, 11, 1)
        val sunTrigger = sunDate.atTime(9, 0).atZone(zone).toInstant()

        // Across fall back, duration between 9am Sat and 9am Sun is exactly 25 HOURS
        val satToSunDuration = Duration.between(satTrigger, sunTrigger)
        assertEquals(25, satToSunDuration.toHours())

        // Both triggers represent exactly 09:00:00 local wall clock time
        assertEquals(9, satDate.atTime(9, 0).atZone(zone).hour)
        assertEquals(9, sunDate.atTime(9, 0).atZone(zone).hour)
    }

    @Test
    fun `skipped local time during spring forward adjusts gracefully without exception`() {
        val zone = ZoneId.of("America/New_York")
        // In America/New_York, on March 8, 2026, 02:30 AM does NOT exist (clocks jump 02:00 -> 03:00)
        val skippedTime = LocalDate.of(2026, 3, 8).atTime(2, 30)
        val zoned = skippedTime.atZone(zone)

        // java.time automatically adjusts forward to 03:30 AM EDT without throwing an exception
        assertEquals(3, zoned.hour)
        assertEquals(30, zoned.minute)
        assertNotNull(zoned.toInstant())
    }

    @Test
    fun `cycle spanning European and Southern Hemisphere DST transitions remains deterministic`() {
        // European Spring Forward: Sunday March 29, 2026 (Europe/Paris)
        val euZone = ZoneId.of("Europe/Paris")
        val euStart = LocalDate.of(2026, 3, 15)
        val euEnd = LocalDate.of(2026, 4, 12)
        assertEquals(28, ChronoUnit.DAYS.between(euStart, euEnd))

        val euSat = LocalDate.of(2026, 3, 28).atTime(9, 0).atZone(euZone).toInstant()
        val euSun = LocalDate.of(2026, 3, 29).atTime(9, 0).atZone(euZone).toInstant()
        assertEquals(23, Duration.between(euSat, euSun).toHours())

        // Southern Hemisphere Fall Back: Sunday April 5, 2026 (Australia/Sydney)
        val auZone = ZoneId.of("Australia/Sydney")
        val auStart = LocalDate.of(2026, 3, 22)
        val auEnd = LocalDate.of(2026, 4, 19)
        assertEquals(28, ChronoUnit.DAYS.between(auStart, auEnd))

        val auSat = LocalDate.of(2026, 4, 4).atTime(9, 0).atZone(auZone).toInstant()
        val auSun = LocalDate.of(2026, 4, 5).atTime(9, 0).atZone(auZone).toInstant()
        assertEquals(25, Duration.between(auSat, auSun).toHours())
    }

    // =========================================================================
    // 5. TYPE CONVERTERS & SERIALIZATION ROUND TRIPS
    // =========================================================================

    @Test
    fun `converters round-trip preserves leap day and year boundary dates`() {
        val datesToTest = listOf(
            LocalDate.of(2024, 2, 29), // Leap day
            LocalDate.of(2025, 12, 31), // New Year's Eve
            LocalDate.of(2026, 1, 1),   // New Year's Day
            LocalDate.of(2000, 2, 29),  // Century leap day
            LocalDate.of(2026, 4, 30),  // 30-day month end
            LocalDate.of(2026, 5, 1)    // Next month start
        )

        for (date in datesToTest) {
            val stringRepresentation = converters.fromLocalDate(date)
            assertNotNull(stringRepresentation)
            val restoredDate = converters.toLocalDate(stringRepresentation)
            assertEquals("Mismatch for $date", date, restoredDate)
        }

        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }
}
