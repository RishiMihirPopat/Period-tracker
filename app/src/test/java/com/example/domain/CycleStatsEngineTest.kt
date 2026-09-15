package com.example.domain

import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleStatsEngineTest {

    private val engine = CycleStatsEngine()

    @Test
    fun `calculates stats accurately for completed cycles`() {
        val today = LocalDate.of(2026, 9, 13)
        val cycles = listOf(
            CycleEntity(
                startDate = today.minusDays(60),
                endDate = today.minusDays(32),
                periodLengthDays = 5,
                cycleLengthDays = 28
            ),
            CycleEntity(
                startDate = today.minusDays(31),
                endDate = today.minusDays(2),
                periodLengthDays = 5,
                cycleLengthDays = 30
            )
        )

        val logs = listOf(
            DailyLogWithSymptoms(
                log = DailyLogEntity(date = today.minusDays(10), mood = listOf("happy")),
                symptoms = listOf(SymptomLogEntity(symptomTag = "headache", severityLevel = 2))
            ),
            DailyLogWithSymptoms(
                log = DailyLogEntity(date = today.minusDays(9), mood = listOf("calm")),
                symptoms = listOf(SymptomLogEntity(symptomTag = "headache", severityLevel = 1))
            )
        )

        val stats = engine.calculateStats(cycles, logs)

        assertEquals(2, stats.totalCyclesTracked)
        assertEquals(29, stats.averageCycleLengthDays)
        assertEquals(5, stats.averagePeriodLengthDays)
        assertEquals(28, stats.shortestCycleDays)
        assertEquals(30, stats.longestCycleDays)
        assertNotNull(stats.cycleLengthStandardDeviation)
        assertTrue(stats.symptomFrequencies.isNotEmpty())
        assertEquals("headache", stats.symptomFrequencies.first().symptomId)
        assertEquals(2, stats.symptomFrequencies.first().count)
    }
}
