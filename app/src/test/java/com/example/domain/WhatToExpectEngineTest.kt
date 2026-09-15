package com.example.domain

import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WhatToExpectEngineTest {

    private val engine = WhatToExpectEngine()

    @Test
    fun `returns appropriate baseline content for each cycle phase`() {
        for (phase in CyclePhase.values()) {
            val content = engine.getExpectation(phase, 1)
            assertEquals(phase, content.phase)
            assertTrue(content.phaseTitle.isNotBlank())
            assertTrue(content.oneWordDescription.isNotBlank())
            assertEquals("Each phase must have exactly 3 body bullets", 3, content.bodyBullets.size)
            content.bodyBullets.forEach { bullet ->
                assertTrue(bullet.isNotBlank())
            }
            assertTrue(content.energyDescription.isNotBlank())
            assertTrue(content.moodDescription.isNotBlank())
            assertTrue(content.plainGuidanceSummary.isNotBlank())
        }

        // Verify that all 4 phases have distinct content
        val menstrual = engine.getExpectation(CyclePhase.MENSTRUAL, 2)
        val follicular = engine.getExpectation(CyclePhase.FOLLICULAR, 8)
        val ovulatory = engine.getExpectation(CyclePhase.OVULATORY, 14)
        val luteal = engine.getExpectation(CyclePhase.LUTEAL, 22)

        val summaries = listOf(
            menstrual.plainGuidanceSummary,
            follicular.plainGuidanceSummary,
            ovulatory.plainGuidanceSummary,
            luteal.plainGuidanceSummary
        )
        assertEquals("Each phase must have a unique plainGuidanceSummary", 4, summaries.toSet().size)

        val energyDescriptions = listOf(
            menstrual.energyDescription,
            follicular.energyDescription,
            ovulatory.energyDescription,
            luteal.energyDescription
        )
        assertEquals("Each phase must have a unique energyDescription", 4, energyDescriptions.toSet().size)

        val moodDescriptions = listOf(
            menstrual.moodDescription,
            follicular.moodDescription,
            ovulatory.moodDescription,
            luteal.moodDescription
        )
        assertEquals("Each phase must have a unique moodDescription", 4, moodDescriptions.toSet().size)
    }

    @Test
    fun `generates personalized insight when recurring symptoms exist in history`() {
        val today = LocalDate.of(2026, 9, 13)
        val historicalLogs = listOf(
            DailyLogWithSymptoms(
                log = DailyLogEntity(date = today.minusDays(28), mood = listOf("calm")),
                symptoms = listOf(
                    SymptomLogEntity(symptomTag = "cramps", severityLevel = 2),
                    SymptomLogEntity(symptomTag = "fatigue", severityLevel = 1)
                )
            ),
            DailyLogWithSymptoms(
                log = DailyLogEntity(date = today.minusDays(56), mood = listOf("calm")),
                symptoms = listOf(
                    SymptomLogEntity(symptomTag = "cramps", severityLevel = 3),
                    SymptomLogEntity(symptomTag = "bloating", severityLevel = 1)
                )
            )
        )

        val expectation = engine.getExpectation(
            phase = CyclePhase.MENSTRUAL,
            cycleDay = 1,
            allLogs = historicalLogs,
            today = today
        )

        assertNotNull(expectation.personalizedInsight)
        assertTrue(expectation.personalizedInsight!!.contains("cramps"))
    }

    @Test
    fun `omits personalized insight when fewer than 2 historical data points exist`() {
        val today = LocalDate.of(2026, 9, 13)
        val singleLog = listOf(
            DailyLogWithSymptoms(
                log = DailyLogEntity(date = today.minusDays(28), mood = listOf("calm")),
                symptoms = listOf(
                    SymptomLogEntity(symptomTag = "cramps", severityLevel = 2)
                )
            )
        )

        val expectation = engine.getExpectation(
            phase = CyclePhase.MENSTRUAL,
            cycleDay = 1,
            allLogs = singleLog,
            today = today
        )

        assertNull(expectation.personalizedInsight)
    }
}
