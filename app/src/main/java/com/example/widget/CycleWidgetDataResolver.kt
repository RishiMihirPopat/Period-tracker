package com.example.widget

import android.content.Context
import com.example.R
import com.example.data.local.CycleDatabase
import com.example.data.model.FlowIntensity
import com.example.domain.CyclePhase
import com.example.domain.CyclePredictionEngine
import com.example.domain.CyclePredictionState
import com.example.domain.PeriodTimingStatus
import com.example.domain.StatisticalPredictionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

class CycleWidgetDataResolver(
    private val context: Context,
    database: CycleDatabase? = null,
    private val predictionEngine: CyclePredictionEngine = CyclePredictionEngine(),
    private val statisticalPredictionEngine: StatisticalPredictionEngine = StatisticalPredictionEngine()
) {
    companion object {
        val DEFAULT_DATA = CycleWidgetData(
            cycleDayText = "Day 1",
            phaseName = "Start Tracking",
            phasePillDrawable = R.drawable.widget_pill_neutral,
            phaseTextColor = R.color.widget_phase_neutral_text,
            phaseDotDrawable = R.drawable.widget_dot_neutral,
            milestoneText = "Tap + to log your period",
            progressPercent = 0,
            progressLabelText = "No cycle data logged yet",
            todayLogText = "Not logged today",
            isTracking = false
        )
    }

    private val database: CycleDatabase = database ?: CycleDatabase.getInstance(context)

    suspend fun resolveWidgetData(today: LocalDate = LocalDate.now()): CycleWidgetData = withContext(Dispatchers.IO) {
        val cycleDao = database.cycleDao()
        val dailyLogDao = database.dailyLogDao()

        val cycles = cycleDao.getAllCyclesSync()
        val todayLog = dailyLogDao.getLogForDateSync(today)
        val allLogs = dailyLogDao.getAllDailyLogsSync()
        val allLogsWithSymptoms = dailyLogDao.getAllLogsWithSymptomsSync()

        if (allLogs.isEmpty() && cycles.isEmpty()) {
            return@withContext DEFAULT_DATA
        }

        val isCurrentlyBleeding = todayLog?.flowIntensity != null && todayLog.flowIntensity != FlowIntensity.NONE
        val completedCycles = cycles.filter { it.cycleLengthDays != null }
        val completedCycleLengths = completedCycles.mapNotNull { it.cycleLengthDays }
        val periodDurations = completedCycles.map { it.periodLengthDays }

        val activeCycle = cycles.firstOrNull { it.endDate == null }
        val lastCompletedCycle = completedCycles.firstOrNull()

        val cycleStartDate = activeCycle?.startDate
            ?: if (allLogs.isNotEmpty()) {
                allLogs.minByOrNull { it.date }?.date ?: today
            } else {
                null
            }

        val allLoggedDates = allLogs.map { it.date }.toSet()
        val stableMarker = statisticalPredictionEngine.findStablePhaseBoundaryMarker(
            today = today,
            currentCycleStartDate = cycleStartDate,
            cycles = cycles,
            allLogs = allLogsWithSymptoms
        )

        val predState = predictionEngine.calculatePrediction(
            today = today,
            currentCycleStartDate = cycleStartDate,
            completedCycleLengths = completedCycleLengths,
            historicalPeriodDurations = periodDurations,
            isCurrentlyBleeding = isCurrentlyBleeding,
            lastCycleEndDate = lastCompletedCycle?.endDate,
            allLoggedDates = allLoggedDates,
            stableSymptomMarker = stableMarker
        )

        val todayLogText = when {
            todayLog?.flowIntensity != null && todayLog.flowIntensity != FlowIntensity.NONE -> {
                val flowName = todayLog.flowIntensity.name.lowercase().replaceFirstChar { it.uppercase() }
                "Logged today: $flowName flow"
            }
            todayLog != null -> "Logged today"
            else -> "Not logged today"
        }

        when (predState) {
            is CyclePredictionState.Predicted -> {
                val day = predState.currentCycleDay
                val cycleLength = predState.predictedCycleLength.coerceAtLeast(1)
                val progress = ((day.toFloat() / cycleLength) * 100).toInt().coerceIn(0, 100)

                val (pillRes, textRes, dotRes) = when (predState.currentPhase) {
                    CyclePhase.MENSTRUAL -> Triple(
                        R.drawable.widget_pill_menstrual,
                        R.color.widget_phase_menstrual_text,
                        R.drawable.widget_dot_menstrual
                    )
                    CyclePhase.FOLLICULAR -> Triple(
                        R.drawable.widget_pill_follicular,
                        R.color.widget_phase_follicular_text,
                        R.drawable.widget_dot_follicular
                    )
                    CyclePhase.OVULATORY -> Triple(
                        R.drawable.widget_pill_ovulation,
                        R.color.widget_phase_ovulation_text,
                        R.drawable.widget_dot_ovulation
                    )
                    CyclePhase.LUTEAL -> Triple(
                        R.drawable.widget_pill_luteal,
                        R.color.widget_phase_luteal_text,
                        R.drawable.widget_dot_luteal
                    )
                }

                val milestone = when (val timing = predState.timingStatus) {
                    is PeriodTimingStatus.Upcoming -> {
                        val d = timing.daysRemaining
                        "Next period in $d day${if (d == 1L) "" else "s"}"
                    }
                    is PeriodTimingStatus.ExpectedToday -> "Period expected today"
                    is PeriodTimingStatus.Late -> {
                        val d = timing.daysLate
                        "Period $d day${if (d == 1L) "" else "s"} late"
                    }
                }

                CycleWidgetData(
                    cycleDayText = "Day $day",
                    phaseName = predState.currentPhase.displayName,
                    phasePillDrawable = pillRes,
                    phaseTextColor = textRes,
                    phaseDotDrawable = dotRes,
                    milestoneText = milestone,
                    progressPercent = progress,
                    progressLabelText = "Cycle day $day of $cycleLength · $progress%",
                    todayLogText = todayLogText,
                    isTracking = true
                )
            }
            is CyclePredictionState.LearningCycle -> {
                val day = predState.currentCycleDay ?: 1
                val totalLength = predState.posteriorCycleLength.roundToInt().coerceAtLeast(1)
                val progress = ((day.toFloat() / totalLength) * 100).toInt().coerceIn(0, 100)
                val isBleeding = predState.isActivelyBleeding

                CycleWidgetData(
                    cycleDayText = "Day $day",
                    phaseName = if (isBleeding) "Period Phase" else "Cycle Day $day",
                    phasePillDrawable = if (isBleeding) R.drawable.widget_pill_menstrual else R.drawable.widget_pill_follicular,
                    phaseTextColor = if (isBleeding) R.color.widget_phase_menstrual_text else R.color.widget_phase_follicular_text,
                    phaseDotDrawable = if (isBleeding) R.drawable.widget_dot_menstrual else R.drawable.widget_dot_follicular,
                    milestoneText = "Learning cycle rhythm",
                    progressPercent = progress,
                    progressLabelText = "Cycle day $day · Learning",
                    todayLogText = todayLogText,
                    isTracking = true
                )
            }
            is CyclePredictionState.AwaitingNextCycle -> {
                val avg = predState.averageCycleLength
                CycleWidgetData(
                    cycleDayText = "Awaiting",
                    phaseName = "Next Cycle",
                    phasePillDrawable = R.drawable.widget_pill_neutral,
                    phaseTextColor = R.color.widget_phase_neutral_text,
                    phaseDotDrawable = R.drawable.widget_dot_neutral,
                    milestoneText = "Avg cycle: $avg days",
                    progressPercent = 100,
                    progressLabelText = "Cycle completed · Waiting next flow",
                    todayLogText = todayLogText,
                    isTracking = true
                )
            }
        }
    }
}
