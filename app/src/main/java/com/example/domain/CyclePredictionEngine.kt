package com.example.domain

import com.example.data.model.FlowIntensity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CyclePhase(val displayName: String) {
    MENSTRUAL("Menstrual Phase"),
    FOLLICULAR("Follicular Phase"),
    OVULATORY("Ovulation Phase"),
    LUTEAL("Luteal Phase")
}

data class FertileWindow(
    val startDate: LocalDate,
    val endDate: LocalDate
)

sealed interface PeriodTimingStatus {
    data class Upcoming(val daysRemaining: Long) : PeriodTimingStatus
    data object ExpectedToday : PeriodTimingStatus
    data class Late(val daysLate: Long) : PeriodTimingStatus
}

sealed interface CyclePredictionState {
    val currentCycleStartDate: LocalDate?

    /**
     * Cold start: User has fewer than 2 completed cycles.
     * Incorporates the published Marquette NFP population prior.
     */
    data class LearningCycle(
        val completedCyclesCount: Int,
        val currentCycleDay: Int?,
        val isActivelyBleeding: Boolean,
        override val currentCycleStartDate: LocalDate? = null,
        val posteriorCycleLength: Double = CyclePredictionEngine.POPULATION_CYCLE_MEAN
    ) : CyclePredictionState

    /**
     * Baseline established (>= 2 cycles), but no active open cycle is in progress yet.
     */
    data class AwaitingNextCycle(
        val completedCyclesCount: Int,
        val averageCycleLength: Int,
        val lastCycleEndDate: LocalDate?,
        override val currentCycleStartDate: LocalDate? = null,
        val posteriorCycleLength: Double = averageCycleLength.toDouble()
    ) : CyclePredictionState

    /**
     * Active cycle in progress with uncertainty-honest ranges, Empirical Bayes shrinkage,
     * adherence-aware missingness widening, and soft biomarker adjustments.
     */
    data class Predicted(
        val currentCycleDay: Int,
        val predictedCycleLength: Int,
        val predictedPeriodStart: LocalDate,
        val predictedPeriodEnd: LocalDate,
        val estimatedOvulationDate: LocalDate,
        val fertileWindow: FertileWindow,
        val currentPhase: CyclePhase,
        val timingStatus: PeriodTimingStatus,
        override val currentCycleStartDate: LocalDate? = null,
        val posteriorCycleLength: Double = predictedCycleLength.toDouble(),
        val posteriorLutealPhase: Double = CyclePredictionEngine.POPULATION_LUTEAL_MEAN,
        val periodRangeStart: LocalDate = predictedPeriodStart,
        val periodRangeEnd: LocalDate = predictedPeriodEnd,
        val ovulationRangeStart: LocalDate = estimatedOvulationDate.minusDays(1),
        val ovulationRangeEnd: LocalDate = estimatedOvulationDate.plusDays(1),
        val adherenceRate: Double = 1.0,
        val isRangeWidenedDueToMissingness: Boolean = false,
        val hasSymptomLutealAdjustment: Boolean = false,
        val symptomAdjustmentName: String? = null,
        val outlierCyclesCount: Int = 0
    ) : CyclePredictionState
}

data class RobustCycleStats(
    val robustMean: Double,
    val effectiveN: Double,
    val outlierCount: Int,
    val median: Double,
    val mad: Double
)

data class AdherenceMetrics(
    val loggingDensity: Double,
    val hasTransitionGap: Boolean,
    val isRangeWidened: Boolean,
    val confidenceMarginDays: Int
)

class CyclePredictionEngine(
    private val defaultLutealPhaseDays: Int = 12,
    private val defaultPeriodDurationDays: Int = 5
) {

    /**
     * Population priors sourced from published clinical fertility research:
     *
     * 1. Bull JR, Rowland SP, Scherwitzl EB, Scherwitzl R, Danielsson KG, Harper J.
     *    "Real-world menstrual cycle characteristics of more than 600,000 menstrual cycles."
     *    npj Digital Medicine. 2019;2:83. doi:10.1038/s41746-019-0152-7.
     *    (Large-scale real-world cohort, n=612,613 cycles across 124,648 women).
     *    - Menstrual Cycle Length: Population Mean = 29.3 days, SD = 5.2 days (95% CI: 16–43 days).
     *    - Luteal Phase Length: Population Mean = 12.4 days, SD = 2.4 days (95% CI: 7–17 days).
     *    - Follicular Phase Length: Population Mean = 16.9 days, SD = 4.5 days (95% CI: 9–30 days).
     *
     * 2. Fehring RJ, Schneider M, Raviele K.
     *    "Variability in the Phases of the Menstrual Cycle."
     *    Journal of Obstetric, Gynecologic, & Neonatal Nursing (JOGNN). 2006;35(3):376-384.
     *    doi:10.1111/j.1552-6909.2006.00051.x.
     *    (Marquette University NFP study, n=1,060 cycles across 141 healthy women).
     *    - Menstrual Cycle Length: Mean = 28.9 days, SD = 3.4 days (95% range: 22–36 days).
     *    - Luteal Phase Length: Mean = 12.4 days, SD = 2.0 days (95% range: 9–16 days).
     *
     * Both empirical studies independently establish the empirical mean luteal phase as 12.4 days.
     * We adopt Bull et al. (2019) as the primary population prior due to its 600,000+ cycle cohort.
     *
     * Shrinkage Strength:
     * k = 3.5 pseudo-cycles (prior stabilizes initial predictions and smoothly fades as personal logs accumulate).
     * posterior = (n_personal * personal_mean + k * population_mean) / (n_personal + k)
     */
    companion object {
        const val POPULATION_CYCLE_MEAN = 29.3
        const val POPULATION_CYCLE_SD = 5.2
        const val POPULATION_LUTEAL_MEAN = 12.4
        const val POPULATION_LUTEAL_SD = 2.4
        const val PRIOR_STRENGTH_K = 3.5
        const val LUTEAL_PRIOR_STRENGTH_K = 3.5

        fun determinePhaseForDate(
            date: LocalDate,
            cycleStartDate: LocalDate,
            cycleLength: Int = 29,
            periodDuration: Int = 5,
            lutealPhaseDays: Int = 12
        ): CyclePhase {
            val daysBetween = ChronoUnit.DAYS.between(cycleStartDate, date).toInt()
            val dayInCycle = ((daysBetween % cycleLength) + cycleLength) % cycleLength + 1
            val ovulationDay = (cycleLength - lutealPhaseDays).coerceAtLeast(periodDuration + 1)
            return when {
                dayInCycle <= periodDuration -> CyclePhase.MENSTRUAL
                dayInCycle < ovulationDay - 5 -> CyclePhase.FOLLICULAR
                dayInCycle <= ovulationDay + 1 -> CyclePhase.OVULATORY
                else -> CyclePhase.LUTEAL
            }
        }
    }

    /**
     * Robust outlier handling using Median Absolute Deviation (MAD).
     * Automatically down-weights extreme cycle lengths without manual user tagging.
     */
    fun calculateRobustCycleStats(cycleLengths: List<Int>): RobustCycleStats {
        if (cycleLengths.isEmpty()) {
            return RobustCycleStats(
                robustMean = POPULATION_CYCLE_MEAN,
                effectiveN = 0.0,
                outlierCount = 0,
                median = POPULATION_CYCLE_MEAN,
                mad = POPULATION_CYCLE_SD
            )
        }

        if (cycleLengths.size == 1) {
            val single = cycleLengths[0].toDouble()
            val z = abs(single - POPULATION_CYCLE_MEAN) / POPULATION_CYCLE_SD
            val weight = if (z <= 2.5) 1.0 else (2.5 / z).coerceIn(0.15, 1.0)
            return RobustCycleStats(
                robustMean = single,
                effectiveN = weight,
                outlierCount = if (z > 2.5) 1 else 0,
                median = single,
                mad = POPULATION_CYCLE_SD
            )
        }

        val sorted = cycleLengths.map { it.toDouble() }.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }

        val absDevs = sorted.map { abs(it - median) }.sorted()
        val rawMad = if (absDevs.size % 2 == 1) {
            absDevs[absDevs.size / 2]
        } else {
            (absDevs[absDevs.size / 2 - 1] + absDevs[absDevs.size / 2]) / 2.0
        }

        // Standard 1.4826 scale factor for normal consistency, with safe floor
        val effectiveMad = (1.4826 * rawMad).coerceAtLeast(1.5)

        var weightedSum = 0.0
        var totalWeight = 0.0
        var outliers = 0

        for (len in sorted) {
            val dev = abs(len - median)
            val z = dev / effectiveMad
            val weight = if (z <= 2.5) {
                1.0
            } else {
                outliers++
                (2.5 / z).coerceIn(0.1, 1.0)
            }
            weightedSum += weight * len
            totalWeight += weight
        }

        val robustMean = if (totalWeight > 0) weightedSum / totalWeight else median

        return RobustCycleStats(
            robustMean = robustMean,
            effectiveN = totalWeight,
            outlierCount = outliers,
            median = median,
            mad = rawMad
        )
    }

    /**
     * Calculates the Empirical Bayes posterior cycle length via shrinkage:
     * posterior = (n_personal * personal_mean + k * population_mean) / (n_personal + k)
     */
    fun calculatePosteriorCycleLength(cycleLengths: List<Int>): Double {
        val stats = calculateRobustCycleStats(cycleLengths)
        val n = stats.effectiveN
        val k = PRIOR_STRENGTH_K
        return (n * stats.robustMean + k * POPULATION_CYCLE_MEAN) / (n + k)
    }

    /**
     * Calculates predicted cycle length using Empirical Bayes shrinkage and MAD downweighting.
     * Returns null for cold start (< 2 cycles) to respect the baseline state gate.
     */
    fun calculatePredictedCycleLength(cycleLengths: List<Int>): Int? {
        if (cycleLengths.size < 2) return null
        return calculatePosteriorCycleLength(cycleLengths).roundToInt()
    }

    /**
     * Adherence/missingness metrics following Li et al. (2022, JAMIA).
     * Distinguishes unlogged days from symptom-free days and detects tracking gaps
     * across phase transitions.
     */
    fun calculateAdherenceMetrics(
        allLoggedDates: Set<LocalDate>,
        currentCycleStartDate: LocalDate?,
        today: LocalDate,
        completedCyclesCount: Int,
        predictedPeriodStart: LocalDate,
        estimatedOvulationDate: LocalDate
    ): AdherenceMetrics {
        val windowStart = currentCycleStartDate ?: today.minusDays(21)
        val windowDays = ChronoUnit.DAYS.between(windowStart, today).toInt() + 1
        val safeWindowDays = windowDays.coerceIn(1, 45)

        var loggedCount = 0
        var currentConsecutiveGap = 0
        var maxConsecutiveGap = 0
        var gapInTransition = false

        val ovulStart = estimatedOvulationDate.minusDays(2)
        val ovulEnd = estimatedOvulationDate.plusDays(2)
        val periodStart = predictedPeriodStart.minusDays(2)
        val periodEnd = predictedPeriodStart.plusDays(2)

        for (i in 0 until safeWindowDays) {
            val checkDate = windowStart.plusDays(i.toLong())
            if (checkDate.isAfter(today)) break

            if (allLoggedDates.contains(checkDate)) {
                loggedCount++
                currentConsecutiveGap = 0
            } else {
                currentConsecutiveGap++
                if (currentConsecutiveGap > maxConsecutiveGap) {
                    maxConsecutiveGap = currentConsecutiveGap
                }
                val inOvul = !checkDate.isBefore(ovulStart) && !checkDate.isAfter(ovulEnd)
                val inPeriod = !checkDate.isBefore(periodStart) && !checkDate.isAfter(periodEnd)
                if ((inOvul || inPeriod) && currentConsecutiveGap >= 3) {
                    gapInTransition = true
                }
            }
        }

        val density = (loggedCount.toDouble() / safeWindowDays.toDouble()).coerceIn(0.0, 1.0)
        val isWidened = gapInTransition || density < 0.60 || (maxConsecutiveGap >= 4 && completedCyclesCount < 4)

        val baseMargin = when {
            completedCyclesCount >= 5 -> 1
            completedCyclesCount >= 2 -> 2
            else -> 3
        }
        val finalMargin = if (isWidened) baseMargin + 2 else baseMargin

        return AdherenceMetrics(
            loggingDensity = density,
            hasTransitionGap = gapInTransition,
            isRangeWidened = isWidened,
            confidenceMarginDays = finalMargin
        )
    }

    /**
     * Soft Bayesian adjustment to personal luteal phase length from stable symptom markers.
     */
    fun calculatePosteriorLutealPhase(
        stableMarker: StableSymptomPhaseMarker?
    ): Double {
        val kLuteal = LUTEAL_PRIOR_STRENGTH_K
        val popLuteal = POPULATION_LUTEAL_MEAN

        if (stableMarker == null || stableMarker.impliedLutealPhaseDays == null) {
            return popLuteal
        }

        val wMarker = 1.5 // Soft weight for the observed biomarker marker
        val implied = stableMarker.impliedLutealPhaseDays.coerceIn(10.0, 18.0)
        val posterior = (kLuteal * popLuteal + wMarker * implied) / (kLuteal + wMarker)
        return posterior.coerceIn(11.0, 17.0)
    }

    /**
     * Primary prediction entry point. Reactive, sequential, and uncertainty-honest.
     */
    fun calculatePrediction(
        today: LocalDate,
        currentCycleStartDate: LocalDate?,
        completedCycleLengths: List<Int>,
        historicalPeriodDurations: List<Int>,
        isCurrentlyBleeding: Boolean,
        lastCycleEndDate: LocalDate? = null,
        allLoggedDates: Set<LocalDate> = emptySet(),
        stableSymptomMarker: StableSymptomPhaseMarker? = null
    ): CyclePredictionState {
        val posteriorCycleLength = calculatePosteriorCycleLength(completedCycleLengths)
        val predictedCycleLength: Int? = calculatePredictedCycleLength(completedCycleLengths)
        val currentCycleDay: Int? = currentCycleStartDate?.let { start ->
            (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
        }

        // Branch 1: Cold start (< 2 cycles)
        if (predictedCycleLength == null) {
            return CyclePredictionState.LearningCycle(
                completedCyclesCount = completedCycleLengths.size,
                currentCycleDay = currentCycleDay,
                isActivelyBleeding = isCurrentlyBleeding,
                currentCycleStartDate = currentCycleStartDate,
                posteriorCycleLength = posteriorCycleLength
            )
        }

        // Branch 2: Baseline exists (>= 2 cycles), but no active cycle start date
        if (currentCycleStartDate == null || currentCycleDay == null) {
            return CyclePredictionState.AwaitingNextCycle(
                completedCyclesCount = completedCycleLengths.size,
                averageCycleLength = predictedCycleLength,
                lastCycleEndDate = lastCycleEndDate,
                currentCycleStartDate = currentCycleStartDate,
                posteriorCycleLength = posteriorCycleLength
            )
        }

        // Branch 3: Active cycle with baseline -> sequential Bayesian computation
        val avgPeriodDuration = if (historicalPeriodDurations.isNotEmpty()) {
            historicalPeriodDurations.average().roundToInt().coerceAtLeast(1)
        } else {
            defaultPeriodDurationDays
        }

        val robustStats = calculateRobustCycleStats(completedCycleLengths)
        val posteriorLuteal = calculatePosteriorLutealPhase(stableSymptomMarker)
        val hasSymptomAdjustment = stableSymptomMarker?.isObservedInCurrentCycle == true &&
            stableSymptomMarker.impliedLutealPhaseDays != null

        val predictedPeriodStart = currentCycleStartDate.plusDays(predictedCycleLength.toLong())
        val predictedPeriodEnd = predictedPeriodStart.plusDays((avgPeriodDuration - 1).toLong().coerceAtLeast(0))

        val estimatedOvulationDate = predictedPeriodStart.minusDays(posteriorLuteal.roundToInt().toLong())
        val fertileWindow = FertileWindow(
            startDate = estimatedOvulationDate.minusDays(5),
            endDate = estimatedOvulationDate.plusDays(1)
        )

        // Adherence and tracking gaps
        val adherence = calculateAdherenceMetrics(
            allLoggedDates = allLoggedDates,
            currentCycleStartDate = currentCycleStartDate,
            today = today,
            completedCyclesCount = completedCycleLengths.size,
            predictedPeriodStart = predictedPeriodStart,
            estimatedOvulationDate = estimatedOvulationDate
        )

        val margin = adherence.confidenceMarginDays.toLong()
        val periodRangeStart = predictedPeriodStart.minusDays(margin)
        val periodRangeEnd = predictedPeriodStart.plusDays(margin + avgPeriodDuration - 1)
        val ovulationRangeStart = estimatedOvulationDate.minusDays(margin.coerceAtMost(2))
        val ovulationRangeEnd = estimatedOvulationDate.plusDays(margin.coerceAtMost(2))

        val currentPhase = determineCyclePhase(
            today = today,
            currentCycleStartDate = currentCycleStartDate,
            periodDurationDays = avgPeriodDuration,
            fertileWindow = fertileWindow,
            isCurrentlyBleeding = isCurrentlyBleeding
        )

        val daysUntilNextPeriod = ChronoUnit.DAYS.between(today, predictedPeriodStart)
        val timingStatus = when {
            daysUntilNextPeriod > 0 -> PeriodTimingStatus.Upcoming(daysUntilNextPeriod)
            daysUntilNextPeriod == 0L -> PeriodTimingStatus.ExpectedToday
            else -> PeriodTimingStatus.Late(abs(daysUntilNextPeriod))
        }

        return CyclePredictionState.Predicted(
            currentCycleDay = currentCycleDay,
            predictedCycleLength = predictedCycleLength,
            predictedPeriodStart = predictedPeriodStart,
            predictedPeriodEnd = predictedPeriodEnd,
            estimatedOvulationDate = estimatedOvulationDate,
            fertileWindow = fertileWindow,
            currentPhase = currentPhase,
            timingStatus = timingStatus,
            currentCycleStartDate = currentCycleStartDate,
            posteriorCycleLength = posteriorCycleLength,
            posteriorLutealPhase = posteriorLuteal,
            periodRangeStart = periodRangeStart,
            periodRangeEnd = periodRangeEnd,
            ovulationRangeStart = ovulationRangeStart,
            ovulationRangeEnd = ovulationRangeEnd,
            adherenceRate = adherence.loggingDensity,
            isRangeWidenedDueToMissingness = adherence.isRangeWidened,
            hasSymptomLutealAdjustment = hasSymptomAdjustment,
            symptomAdjustmentName = stableSymptomMarker?.symptomTag,
            outlierCyclesCount = robustStats.outlierCount
        )
    }

    fun determinePhaseForDate(
        date: LocalDate,
        cycleStartDate: LocalDate,
        cycleLength: Int = 28,
        periodDuration: Int = 5
    ): CyclePhase = determinePhaseForDate(
        date = date,
        cycleStartDate = cycleStartDate,
        cycleLength = cycleLength,
        periodDuration = periodDuration,
        lutealPhaseDays = defaultLutealPhaseDays
    )

    private fun determineCyclePhase(
        today: LocalDate,
        currentCycleStartDate: LocalDate,
        periodDurationDays: Int,
        fertileWindow: FertileWindow,
        isCurrentlyBleeding: Boolean
    ): CyclePhase {
        val periodEndDate = currentCycleStartDate.plusDays((periodDurationDays - 1).toLong().coerceAtLeast(0))

        return when {
            isCurrentlyBleeding || (!today.isBefore(currentCycleStartDate) && !today.isAfter(periodEndDate)) -> {
                CyclePhase.MENSTRUAL
            }
            today.isBefore(fertileWindow.startDate) -> {
                CyclePhase.FOLLICULAR
            }
            !today.isBefore(fertileWindow.startDate) && !today.isAfter(fertileWindow.endDate) -> {
                CyclePhase.OVULATORY
            }
            else -> {
                CyclePhase.LUTEAL
            }
        }
    }
}
