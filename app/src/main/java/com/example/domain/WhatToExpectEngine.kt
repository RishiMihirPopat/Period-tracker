package com.example.domain

import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.model.EnergyLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Educational, empathetic phase-specific content and personalized insights
 * calculated from the user's on-device history.
 */
data class PhaseExpectation(
    val phase: CyclePhase,
    val phaseTitle: String,
    val oneWordDescription: String,
    val bodyBullets: List<String>,
    val energyDescription: String,
    val moodDescription: String,
    val plainGuidanceSummary: String,
    val personalizedInsight: String? = null
)

class WhatToExpectEngine {

    /**
     * Generates phase-specific expectation along with personalized history matching
     * and statistical model predictions (when data threshold is met).
     */
    fun getExpectation(
        phase: CyclePhase,
        cycleDay: Int?,
        allLogs: List<DailyLogWithSymptoms> = emptyList(),
        cycles: List<CycleEntity> = emptyList(),
        today: LocalDate = LocalDate.now(),
        statisticalPredictions: StatisticalPredictions? = null
    ): PhaseExpectation {
        val base = getBasePhaseContent(phase)
        val personalized = generatePersonalizedInsight(
            phase = phase,
            currentCycleDay = cycleDay,
            allLogs = allLogs,
            cycles = cycles,
            today = today,
            statisticalPredictions = statisticalPredictions
        )

        return base.copy(personalizedInsight = personalized)
    }

    private fun getBasePhaseContent(phase: CyclePhase): PhaseExpectation {
        return when (phase) {
            CyclePhase.MENSTRUAL -> PhaseExpectation(
                phase = CyclePhase.MENSTRUAL,
                phaseTitle = "Menstrual Phase",
                oneWordDescription = "Reset",
                bodyBullets = listOf(
                    "Your body is resting and clearing out tension.",
                    "Gentle warmth and light stretching can ease lingering aches.",
                    "Stay well hydrated and nourish yourself with warm, hearty food."
                ),
                energyDescription = "Resting low — give yourself permission to pace quietly.",
                moodDescription = "Inward, quiet, and reflective.",
                plainGuidanceSummary = "Your body is naturally resetting. Keep physical exertion light, nourish yourself with warm meals, and prioritize restful sleep without guilt."
            )
            CyclePhase.FOLLICULAR -> PhaseExpectation(
                phase = CyclePhase.FOLLICULAR,
                phaseTitle = "Follicular Phase",
                oneWordDescription = "Rise",
                bodyBullets = listOf(
                    "Your physical stamina and natural endurance are building back up.",
                    "Muscles recover faster and you may feel noticeably lighter on your feet.",
                    "A great time to try new movement routines or brisk outdoor walks."
                ),
                energyDescription = "Rising steadily day by day with fresh alertness.",
                moodDescription = "Curious, motivated, and socially open.",
                plainGuidanceSummary = "Energy and mental clarity are climbing steadily. It is an ideal window to start fresh projects, schedule social plans, and lean into active movement."
            )
            CyclePhase.OVULATORY -> PhaseExpectation(
                phase = CyclePhase.OVULATORY,
                phaseTitle = "Ovulatory Phase",
                oneWordDescription = "Peak",
                bodyBullets = listOf(
                    "Your body temperature and physical vitality are at their monthly peak.",
                    "Skin often looks radiant and overall coordination feels sharp.",
                    "You might notice a subtle brief flutter on one side of your lower abdomen."
                ),
                energyDescription = "High and vibrant with effortless stamina.",
                moodDescription = "Confident, outgoing, and communicative.",
                plainGuidanceSummary = "Vitality and communication are at monthly highs. Channel this natural surge into demanding tasks, meaningful conversations, and energizing activities."
            )
            CyclePhase.LUTEAL -> PhaseExpectation(
                phase = CyclePhase.LUTEAL,
                phaseTitle = "Luteal Phase",
                oneWordDescription = "Wind-down",
                bodyBullets = listOf(
                    "Your body temperature stays slightly warmer as metabolic appetite grows.",
                    "Mild puffiness or breast tenderness can gently show up.",
                    "Slow, grounded movement like yoga or walking feels especially restorative."
                ),
                energyDescription = "Slowing down — best suited for focused, methodical tasks.",
                moodDescription = "Observant, sensitive, and craving comforting spaces.",
                plainGuidanceSummary = "Your body is winding down toward its next cycle. Balance your schedule with calming routines, nourishing snacks, and quiet evenings."
            )
        }
    }

    /**
     * Pattern-matches against the user's historical logged symptoms and moods
     * at the same cycle-day and phase position, and folds in statistical predictions
     * if available.
     * Only returns an insight if at least 2 relevant past data points support it,
     * or if statistical predictions are available.
     */
    private fun generatePersonalizedInsight(
        phase: CyclePhase,
        currentCycleDay: Int?,
        allLogs: List<DailyLogWithSymptoms>,
        cycles: List<CycleEntity>,
        today: LocalDate,
        statisticalPredictions: StatisticalPredictions?
    ): String? {
        val sentences = mutableListOf<String>()

        // 1. Fold in Statistical Predictions if available
        if (statisticalPredictions != null) {
            // Symptoms prediction with severity
            val topSymptom = statisticalPredictions.symptoms.firstOrNull()
            if (topSymptom != null) {
                val formattedName = topSymptom.symptomTag.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val pct = (topSymptom.occurrenceProbability * 100).roundToInt()
                if (topSymptom.severitySummary != null) {
                    sentences.add("$formattedName has tended to run ${topSymptom.severitySummary.removePrefix("tending to run ")} around now (~$pct% chance).")
                } else {
                    sentences.add("$formattedName is likely around now (~$pct% chance).")
                }
            }

            // Energy prediction
            if (statisticalPredictions.energy != null && sentences.size < 2) {
                sentences.add(statisticalPredictions.energy.summaryText)
            }

            // Mood prediction
            if (statisticalPredictions.moods.isNotEmpty() && sentences.size < 2) {
                val moods = statisticalPredictions.moods.take(2)
                val moodText = moods.joinToString(" and ") {
                    "${it.moodTag.lowercase()} (~${(it.probability * 100).roundToInt()}%)"
                }
                sentences.add("$moodText tend to be your most likely mood states around this time.")
            }

            // Sleep prediction
            if (statisticalPredictions.sleep != null && sentences.size < 2) {
                sentences.add(statisticalPredictions.sleep.summaryText)
            }

            if (sentences.isNotEmpty()) {
                return sentences.take(2).joinToString(" ")
            }
        }

        // 2. Historical Pattern Matching Fallback (at least 2 relevant data points)
        val pastLogs = allLogs.filter { it.log.date.isBefore(today) }
        if (pastLogs.size < 2) return null

        val matchedLogs = pastLogs.filter { logWithSymptoms ->
            val log = logWithSymptoms.log
            val matchingCycle = cycles.firstOrNull { cycle ->
                val end = cycle.endDate ?: cycle.startDate.plusDays(40)
                !log.date.isBefore(cycle.startDate) && !log.date.isAfter(end)
            }
            if (matchingCycle != null && currentCycleDay != null) {
                val pastCycleDay = ChronoUnit.DAYS.between(matchingCycle.startDate, log.date).toInt() + 1
                abs(pastCycleDay - currentCycleDay) <= 2
            } else {
                val daysAgo = ChronoUnit.DAYS.between(log.date, today)
                daysAgo in 15..180 && (daysAgo % 28 in 0..3 || daysAgo % 28 in 25..27)
            }
        }

        if (matchedLogs.size < 2) return null

        // Symptom recurrences
        val symptomCounts = mutableMapOf<String, Int>()
        for (log in matchedLogs) {
            for (sym in log.symptoms) {
                symptomCounts[sym.symptomTag] = (symptomCounts[sym.symptomTag] ?: 0) + 1
            }
        }
        val topSymptom = symptomCounts.filter { it.value >= 2 }.maxByOrNull { it.value }?.key

        // Energy trend
        val lowEnergyCount = matchedLogs.count { it.log.energyLevel == EnergyLevel.LOW }
        val highEnergyCount = matchedLogs.count { it.log.energyLevel == EnergyLevel.HIGH || it.log.energyLevel == EnergyLevel.PEAK }

        // Mood recurrences
        val moodCounts = mutableMapOf<String, Int>()
        for (log in matchedLogs) {
            for (m in log.log.mood) {
                moodCounts[m] = (moodCounts[m] ?: 0) + 1
            }
        }
        val topMood = moodCounts.filter { it.value >= 2 }.maxByOrNull { it.value }?.key

        if (topSymptom != null) {
            val formattedSymptom = topSymptom.lowercase().replace('_', ' ')
            val pastSeverities = matchedLogs.flatMap { it.symptoms }
                .filter { it.symptomTag.equals(topSymptom, ignoreCase = true) }
                .map { it.severityLevel }
            val sevNote = if (pastSeverities.isNotEmpty()) {
                val avg = pastSeverities.average()
                when {
                    avg >= 4.2 -> "; ${formattedSymptom} tended to run very severe"
                    avg >= 3.4 -> "; ${formattedSymptom} tended to run severe"
                    avg >= 2.4 -> "; ${formattedSymptom} tended to run moderate"
                    avg >= 1.6 -> "; ${formattedSymptom} tended to run mild"
                    else -> "; ${formattedSymptom} tended to run very mild"
                }
            } else ""
            sentences.add("You've logged $formattedSymptom here before$sevNote.")
        }

        if (lowEnergyCount >= 2) {
            sentences.add("Energy tends to dip around now.")
        } else if (highEnergyCount >= 2) {
            sentences.add("Energy tends to peak around this time.")
        } else if (topMood != null) {
            val formattedMood = topMood.lowercase().replace('_', ' ')
            sentences.add("You've often noted feeling $formattedMood around this time.")
        }

        return if (sentences.isNotEmpty()) {
            sentences.take(2).joinToString(" ")
        } else {
            null
        }
    }
}
