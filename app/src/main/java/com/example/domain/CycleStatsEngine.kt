package com.example.domain

import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.SymptomLogEntity
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CycleStats(
    val totalCyclesTracked: Int,
    val averageCycleLengthDays: Int?,
    val averagePeriodLengthDays: Int?,
    val shortestCycleDays: Int?,
    val longestCycleDays: Int?,
    val cycleLengthStandardDeviation: Double?,
    val regularityScore: String, // "High regularity", "Moderate variation", "Irregular"
    val symptomFrequencies: List<SymptomFrequency>,
    val moodFrequencies: List<MoodFrequency>
)

data class SymptomFrequency(
    val symptomId: String,
    val count: Int,
    val percentage: Int
)

data class MoodFrequency(
    val moodId: String,
    val count: Int,
    val percentage: Int
)

class CycleStatsEngine {

    fun calculateStats(
        cycles: List<CycleEntity>,
        logs: List<DailyLogWithSymptoms>
    ): CycleStats {
        val completedCycles = cycles.filter { it.cycleLengthDays != null }
        val cycleLengths = completedCycles.mapNotNull { it.cycleLengthDays }
        val periodLengths = cycles.map { it.periodLengthDays }

        val avgCycleLength = if (cycleLengths.isNotEmpty()) {
            cycleLengths.average().roundToInt()
        } else null

        val avgPeriodLength = if (periodLengths.isNotEmpty()) {
            periodLengths.average().roundToInt()
        } else null

        val shortest = cycleLengths.minOrNull()
        val longest = cycleLengths.maxOrNull()

        val stdDev = if (cycleLengths.size >= 2) {
            val mean = cycleLengths.average()
            val variance = cycleLengths.sumOf { (it - mean) * (it - mean) } / cycleLengths.size
            sqrt(variance)
        } else null

        val regularity = when {
            stdDev == null -> "Awaiting more data (needs 2+ cycles)"
            stdDev <= 2.0 -> "High regularity (variation < 2 days)"
            stdDev <= 4.5 -> "Moderate variation (typical cycle shift)"
            else -> "Irregular variation (> 4.5 days shift)"
        }

        // Symptom breakdown
        val totalDaysLogged = logs.size.coerceAtLeast(1)
        val symptomCounts = mutableMapOf<String, Int>()
        val moodCounts = mutableMapOf<String, Int>()

        for (item in logs) {
            for (sym in item.symptoms) {
                symptomCounts[sym.symptomTag] = (symptomCounts[sym.symptomTag] ?: 0) + 1
            }
            for (m in item.log.mood) {
                moodCounts[m] = (moodCounts[m] ?: 0) + 1
            }
        }

        val symptomFrequencies = symptomCounts.map { (sym, count) ->
            SymptomFrequency(
                symptomId = sym,
                count = count,
                percentage = ((count.toDouble() / totalDaysLogged) * 100).roundToInt()
            )
        }.sortedByDescending { it.count }

        val moodFrequencies = moodCounts.map { (mood, count) ->
            MoodFrequency(
                moodId = mood,
                count = count,
                percentage = ((count.toDouble() / totalDaysLogged) * 100).roundToInt()
            )
        }.sortedByDescending { it.count }

        return CycleStats(
            totalCyclesTracked = completedCycles.size,
            averageCycleLengthDays = avgCycleLength,
            averagePeriodLengthDays = avgPeriodLength,
            shortestCycleDays = shortest,
            longestCycleDays = longest,
            cycleLengthStandardDeviation = stdDev,
            regularityScore = regularity,
            symptomFrequencies = symptomFrequencies,
            moodFrequencies = moodFrequencies
        )
    }
}
