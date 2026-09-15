package com.example.domain

import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.model.EnergyLevel
import com.example.data.model.SleepQuality
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SymptomPrediction(
    val symptomTag: String,
    val occurrenceProbability: Double,
    val severitySummary: String? = null
)

data class MoodPrediction(
    val moodTag: String,
    val probability: Double
)

data class EnergyPrediction(
    val summaryText: String,
    val probability: Double
)

data class SleepPrediction(
    val summaryText: String,
    val probability: Double
)

data class StatisticalPredictions(
    val symptoms: List<SymptomPrediction> = emptyList(),
    val moods: List<MoodPrediction> = emptyList(),
    val energy: EnergyPrediction? = null,
    val sleep: SleepPrediction? = null
)

/**
 * Phase-boundary biomarker adjustment marker. Represents a symptom whose probability peak
 * clusters tightly and consistently across completed cycles in the late-luteal / premenstrual window.
 */
data class StableSymptomPhaseMarker(
    val symptomTag: String,
    val meanDaysBeforePeriod: Double,
    val standardDeviation: Double,
    val cycleConsistencyRate: Double,
    val isObservedInCurrentCycle: Boolean = false,
    val currentCycleDayObserved: Int? = null,
    val impliedLutealPhaseDays: Double? = null
)

class StatisticalPredictionEngine {

    companion object {
        const val MIN_COMPLETED_CYCLES = 3
        const val MIN_LOGGED_DAYS = 40

        val ALL_MOOD_CATEGORIES = listOf(
            "happy", "calm", "irritable", "sad",
            "tired", "energized", "anxious", "loving"
        )
    }

    /**
     * Runs statistical prediction models only if the gating criteria are met:
     * - at least 3 completed cycles
     * - at least 40 logged days total
     * Returns null if gate is not passed or no valid cycle day position exists.
     */
    fun computePredictions(
        today: LocalDate,
        currentCycleDay: Int?,
        predictedCycleLength: Int?,
        cycles: List<CycleEntity>,
        allLogs: List<DailyLogWithSymptoms>
    ): StatisticalPredictions? {
        val completedCycles = cycles.filter { it.cycleLengthDays != null }
        if (completedCycles.size < MIN_COMPLETED_CYCLES || allLogs.size < MIN_LOGGED_DAYS) {
            return null // Hard gate: must meet minimum data threshold
        }

        if (currentCycleDay == null || predictedCycleLength == null || predictedCycleLength <= 0) {
            return null
        }

        val targetX = (currentCycleDay.toDouble() / predictedCycleLength.toDouble()).coerceIn(0.0, 1.2)

        // Build training data by mapping each historical log to its cycle-day-position
        val indexedLogs = mutableListOf<Pair<Double, DailyLogWithSymptoms>>()
        for (logWithSymptoms in allLogs) {
            val logDate = logWithSymptoms.log.date
            val matchingCycle = completedCycles.firstOrNull { cycle ->
                val end = cycle.endDate ?: cycle.startDate.plusDays((cycle.cycleLengthDays ?: 28).toLong())
                !logDate.isBefore(cycle.startDate) && !logDate.isAfter(end)
            }
            if (matchingCycle != null) {
                val cycleLen = matchingCycle.cycleLengthDays ?: 28
                val dayInCycle = (ChronoUnit.DAYS.between(matchingCycle.startDate, logDate) + 1).toInt()
                val x = (dayInCycle.toDouble() / cycleLen.toDouble()).coerceIn(0.0, 1.2)
                indexedLogs.add(x to logWithSymptoms)
            }
        }

        if (indexedLogs.size < 10) return null

        // 1. Symptoms Hurdle Model
        val symptomPredictions = computeSymptomPredictions(indexedLogs, targetX)

        // 2. Mood Binary Models (8 categories)
        val moodPredictions = computeMoodPredictions(indexedLogs, targetX)

        // 3. Energy Ordinal Model (k=5)
        val energyPrediction = computeEnergyPrediction(indexedLogs, targetX)

        // 4. Sleep Ordinal Model (k=4)
        val sleepPrediction = computeSleepPrediction(indexedLogs, targetX)

        return StatisticalPredictions(
            symptoms = symptomPredictions,
            moods = moodPredictions,
            energy = energyPrediction,
            sleep = sleepPrediction
        )
    }

    private fun computeSymptomPredictions(
        indexedLogs: List<Pair<Double, DailyLogWithSymptoms>>,
        targetX: Double
    ): List<SymptomPrediction> {
        // Collect all distinct symptoms logged
        val allSymptomTags = indexedLogs.flatMap { it.second.symptoms.map { s -> s.symptomTag } }.distinct()
        val predictions = mutableListOf<SymptomPrediction>()

        for (tag in allSymptomTags) {
            // Hurdle Part 1: Occurrence (k=2)
            val occData = indexedLogs.map { (x, logWithSymptoms) ->
                val hasSymptom = logWithSymptoms.symptoms.any { it.symptomTag.equals(tag, ignoreCase = true) }
                x to if (hasSymptom) 1 else 0
            }
            val occModel = CumulativeLogitModel(numCategories = 2)
            val occFit = occModel.fit(occData)
            val occProb = if (occFit != null) {
                occModel.predictProbability(occFit, targetX, category = 1)
            } else {
                occData.count { it.second == 1 }.toDouble() / occData.size.toDouble()
            }

            // Only surface if probability is notable (>= 35%)
            if (occProb < 0.35) continue

            // Hurdle Part 2: Severity given occurrence (k=5: 1=Very Mild, 2=Mild, 3=Moderate, 4=Severe, 5=Very Severe)
            val severityLogs = indexedLogs.mapNotNull { (x, logWithSymptoms) ->
                val found = logWithSymptoms.symptoms.firstOrNull { it.symptomTag.equals(tag, ignoreCase = true) }
                if (found != null) {
                    // severityLevel is 1..5 -> map to 0..4
                    val level = (found.severityLevel - 1).coerceIn(0, 4)
                    x to level
                } else null
            }

            var severitySummary: String? = null
            if (severityLogs.size >= 10) {
                val sevModel = CumulativeLogitModel(numCategories = 5)
                val sevFit = sevModel.fit(severityLogs)
                if (sevFit != null) {
                    val probs = sevFit.predictProbabilities(targetX)
                    val pVeryMild = probs[0]
                    val pMild = probs[1]
                    val pMod = probs[2]
                    val pSev = probs[3]
                    val pVerySev = probs[4]
                    severitySummary = when {
                        pSev + pVerySev >= 0.5 -> "tending to run severe to very severe"
                        pVerySev >= 0.4 -> "tending to run very severe"
                        pSev >= 0.4 -> "tending to run severe"
                        pMod >= 0.4 -> "tending to run moderate"
                        pVeryMild + pMild >= 0.55 -> "tending to run mild"
                        pVeryMild >= 0.4 -> "tending to run very mild"
                        pMild >= 0.4 -> "tending to run mild"
                        pMod + pSev + pVerySev >= 0.6 -> "tending to run moderate to severe"
                        else -> "tending to run moderate"
                    }
                }
            }
            if (severitySummary == null && severityLogs.isNotEmpty()) {
                // Fallback to empirical summary based on logged average
                val avgSev = severityLogs.map { it.second + 1 }.average() // 1.0 to 5.0
                severitySummary = when {
                    avgSev >= 4.2 -> "tending to run very severe"
                    avgSev >= 3.4 -> "tending to run severe"
                    avgSev >= 2.4 -> "tending to run moderate"
                    avgSev >= 1.6 -> "tending to run mild"
                    else -> "tending to run very mild"
                }
            }

            predictions.add(
                SymptomPrediction(
                    symptomTag = tag,
                    occurrenceProbability = occProb,
                    severitySummary = severitySummary
                )
            )
        }

        return predictions.sortedByDescending { it.occurrenceProbability }
    }

    private fun computeMoodPredictions(
        indexedLogs: List<Pair<Double, DailyLogWithSymptoms>>,
        targetX: Double
    ): List<MoodPrediction> {
        val predictions = mutableListOf<MoodPrediction>()
        val moodModel = CumulativeLogitModel(numCategories = 2)

        for (mood in ALL_MOOD_CATEGORIES) {
            val moodData = indexedLogs.map { (x, logWithSymptoms) ->
                val hasMood = logWithSymptoms.log.mood.any { it.equals(mood, ignoreCase = true) }
                x to if (hasMood) 1 else 0
            }
            val fit = moodModel.fit(moodData) ?: continue
            val prob = moodModel.predictProbability(fit, targetX, category = 1)
            if (prob >= 0.35) {
                predictions.add(MoodPrediction(moodTag = mood, probability = prob))
            }
        }
        return predictions.sortedByDescending { it.probability }
    }

    private fun computeEnergyPrediction(
        indexedLogs: List<Pair<Double, DailyLogWithSymptoms>>,
        targetX: Double
    ): EnergyPrediction? {
        val energyData = indexedLogs.mapNotNull { (x, logWithSymptoms) ->
            logWithSymptoms.log.energyLevel?.let { x to it.ordinal.coerceIn(0, 4) }
        }
        if (energyData.size < 10) return null

        val model = CumulativeLogitModel(numCategories = 5)
        val fit = model.fit(energyData) ?: return null
        val probs = fit.predictProbabilities(targetX)

        val pLow = probs[0] + probs[1]
        val pBal = probs[2]
        val pHigh = probs[3] + probs[4]

        return when {
            pLow >= 0.5 -> EnergyPrediction(
                summaryText = "Energy is likely to run lower (~${(pLow * 100).roundToInt()}% probability of low or very low)",
                probability = pLow
            )
            pHigh >= 0.5 -> EnergyPrediction(
                summaryText = "Energy is likely to run higher (~${(pHigh * 100).roundToInt()}% probability of high or peak)",
                probability = pHigh
            )
            pBal >= 0.4 -> EnergyPrediction(
                summaryText = "Energy is likely to be balanced (~${(pBal * 100).roundToInt()}% probability)",
                probability = pBal
            )
            else -> null
        }
    }

    private fun computeSleepPrediction(
        indexedLogs: List<Pair<Double, DailyLogWithSymptoms>>,
        targetX: Double
    ): SleepPrediction? {
        val sleepData = indexedLogs.mapNotNull { (x, logWithSymptoms) ->
            logWithSymptoms.log.sleepQuality?.let { x to it.ordinal.coerceIn(0, 3) }
        }
        if (sleepData.size < 10) return null

        val model = CumulativeLogitModel(numCategories = 4)
        val fit = model.fit(sleepData) ?: return null
        val probs = fit.predictProbabilities(targetX)

        val pRestless = probs[0] + probs[1]
        val pGood = probs[2] + probs[3]

        return when {
            pRestless >= 0.5 -> SleepPrediction(
                summaryText = "Sleep may tend to be restless (~${(pRestless * 100).roundToInt()}% probability)",
                probability = pRestless
            )
            pGood >= 0.5 -> SleepPrediction(
                summaryText = "Restful sleep is likely (~${(pGood * 100).roundToInt()}% probability of good or deep sleep)",
                probability = pGood
            )
            else -> null
        }
    }

    private fun CumulativeLogitModel.predictProbability(
        fit: CumulativeLogitModel.FitResult,
        x: Double,
        category: Int
    ): Double {
        val probs = fit.predictProbabilities(x)
        return if (category in probs.indices) probs[category] else 0.0
    }

    /**
     * Finds symptoms with tightly clustered, statistically stable probability peaks
     * relative to cycle end (premenstrual window 1..7 days before period).
     *
     * Strict criteria (Requirement 4):
     * - Minimum data gate: at least 3 completed cycles and 40 logged days.
     * - Minimum consistency: symptom must occur in at least 60% of completed cycles in the late window.
     * - Minimum clustering tightness: standard deviation <= 1.8 days.
     *
     * If observed in the current cycle, provides an implied luteal phase estimate for soft Bayesian
     * blending with the calendar-based model.
     */
    fun findStablePhaseBoundaryMarker(
        today: LocalDate,
        currentCycleStartDate: LocalDate?,
        cycles: List<CycleEntity>,
        allLogs: List<DailyLogWithSymptoms>
    ): StableSymptomPhaseMarker? {
        val completedCycles = cycles.filter { it.cycleLengthDays != null && it.cycleLengthDays!! in 20..45 }
        if (completedCycles.size < MIN_COMPLETED_CYCLES || allLogs.size < MIN_LOGGED_DAYS) {
            return null
        }

        val allSymptomTags = allLogs.flatMap { it.symptoms.map { s -> s.symptomTag } }.distinct()
        var bestMarker: StableSymptomPhaseMarker? = null

        for (tag in allSymptomTags) {
            val cycleOffsets = mutableListOf<Double>()
            var cyclesWithSymptom = 0

            for (cycle in completedCycles) {
                val cycleLen = cycle.cycleLengthDays ?: 28
                val cycleEnd = cycle.endDate ?: cycle.startDate.plusDays(cycleLen.toLong())
                val cycleStart = cycle.startDate

                val lateLogs = allLogs.filter { logItem ->
                    val d = logItem.log.date
                    !d.isBefore(cycleStart) && !d.isAfter(cycleEnd) &&
                        logItem.symptoms.any { it.symptomTag.equals(tag, ignoreCase = true) }
                }

                val offsets = lateLogs.mapNotNull { logItem ->
                    val daysBefore = ChronoUnit.DAYS.between(logItem.log.date, cycleEnd).toInt()
                    if (daysBefore in 1..7) daysBefore.toDouble() else null
                }

                if (offsets.isNotEmpty()) {
                    cyclesWithSymptom++
                    cycleOffsets.add(offsets.average())
                }
            }

            val consistency = cyclesWithSymptom.toDouble() / completedCycles.size.toDouble()
            if (consistency < 0.60 || cycleOffsets.size < 2) continue

            val meanOffset = cycleOffsets.average()
            val variance = cycleOffsets.sumOf { (it - meanOffset) * (it - meanOffset) } / cycleOffsets.size
            val sd = sqrt(variance)

            // Must cluster tightly around a stable point in her cycle
            if (sd <= 1.8) {
                if (bestMarker == null || consistency > bestMarker.cycleConsistencyRate ||
                    (consistency == bestMarker.cycleConsistencyRate && sd < bestMarker.standardDeviation)) {
                    bestMarker = StableSymptomPhaseMarker(
                        symptomTag = tag,
                        meanDaysBeforePeriod = meanOffset,
                        standardDeviation = sd,
                        cycleConsistencyRate = consistency
                    )
                }
            }
        }

        if (bestMarker == null) return null

        // Check if this stable marker was logged in the current cycle
        if (currentCycleStartDate != null) {
            val currentLogs = allLogs.filter {
                !it.log.date.isBefore(currentCycleStartDate) && !it.log.date.isAfter(today) &&
                    it.symptoms.any { s -> s.symptomTag.equals(bestMarker.symptomTag, ignoreCase = true) }
            }

            val currentDaysObserved = currentLogs.map {
                (ChronoUnit.DAYS.between(currentCycleStartDate, it.log.date).toInt() + 1)
            }.filter { it >= 10 } // in post-follicular / luteal timing

            if (currentDaysObserved.isNotEmpty()) {
                val latestObservedDay = currentDaysObserved.maxOrNull()!!
                // Estimated period start = latestObservedDay + meanDaysBeforePeriod
                // Implied luteal length = (latestObservedDay + meanDaysBeforePeriod) - estimatedOvulationDay (~16.9 from Bull et al.)
                val impliedLuteal = ((latestObservedDay + bestMarker.meanDaysBeforePeriod) - 16.9).coerceIn(8.0, 18.0)
                return bestMarker.copy(
                    isObservedInCurrentCycle = true,
                    currentCycleDayObserved = latestObservedDay,
                    impliedLutealPhaseDays = impliedLuteal
                )
            }
        }

        return bestMarker
    }
}
