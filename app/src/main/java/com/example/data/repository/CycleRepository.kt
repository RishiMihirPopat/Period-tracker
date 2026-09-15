package com.example.data.repository

import com.example.data.local.dao.CycleDao
import com.example.data.local.dao.DailyLogDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SymptomLogDao
import com.example.data.local.dao.UserSettingsDao
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.PastCycleInput
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import com.example.domain.CyclePredictionEngine
import com.example.domain.CyclePredictionState
import com.example.domain.StatisticalPredictionEngine
import com.example.domain.StatisticalPredictions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.example.data.backup.BackupMetadata
import com.example.data.backup.BackupRestoreResult
import com.example.data.backup.CycleBackupManager
import android.content.Context
import android.net.Uri
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import com.example.data.report.DoctorReportGenerator
import com.example.data.report.DoctorReportData
import com.example.data.report.ReportFormat

class CycleRepository(
    private val dailyLogDao: DailyLogDao,
    private val symptomLogDao: SymptomLogDao,
    private val cycleDao: CycleDao,
    private val userSettingsDao: UserSettingsDao,
    private val profileDao: ProfileDao,
    private val predictionEngine: CyclePredictionEngine = CyclePredictionEngine(),
    private val statisticalPredictionEngine: StatisticalPredictionEngine = StatisticalPredictionEngine(),
    private val backupManager: CycleBackupManager? = null,
    doctorReportGenerator: DoctorReportGenerator? = null,
    private val context: Context? = null
) {
    private val doctorReportGenerator: DoctorReportGenerator = doctorReportGenerator ?: DoctorReportGenerator(
        cycleDao = cycleDao,
        dailyLogDao = dailyLogDao,
        symptomLogDao = symptomLogDao,
        profileDao = profileDao,
        userSettingsDao = userSettingsDao
    )

    fun getProfile(): Flow<ProfileEntity?> = profileDao.getProfile()

    suspend fun getProfileSync(): ProfileEntity? = profileDao.getProfileSync()

    suspend fun saveProfile(profile: ProfileEntity) {
        profileDao.insertOrUpdateProfile(profile)
    }

    suspend fun completeOnboarding(name: String) {
        profileDao.insertOrUpdateProfile(
            ProfileEntity(
                id = 1,
                name = name.trim(),
                onboardingCompletedAt = System.currentTimeMillis()
            )
        )
        context?.let { ctx ->
            com.example.widget.CycleWidgetProvider.updateAllWidgets(ctx)
        }
    }

    fun getAllDailyLogs(): Flow<List<DailyLogEntity>> = dailyLogDao.getAllDailyLogs()

    fun getLogForDate(date: LocalDate): Flow<DailyLogEntity?> = dailyLogDao.getLogForDate(date)

    fun getLogWithSymptoms(date: LocalDate): Flow<DailyLogWithSymptoms?> = dailyLogDao.getLogWithSymptoms(date)

    fun getLogsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLogEntity>> =
        dailyLogDao.getLogsBetween(startDate, endDate)

    fun getLogsWithSymptomsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLogWithSymptoms>> =
        dailyLogDao.getLogsWithSymptomsBetween(startDate, endDate)

    fun getAllLogsWithSymptoms(): Flow<List<DailyLogWithSymptoms>> = dailyLogDao.getAllLogsWithSymptoms()

    fun getAllCycles(): Flow<List<CycleEntity>> = cycleDao.getAllCycles()

    fun getUserSettings(): Flow<UserSettingsEntity?> = userSettingsDao.getSettings()

    suspend fun updateSettings(settings: UserSettingsEntity) {
        userSettingsDao.insertOrUpdateSettings(settings)
    }

    fun getAllFlowLogs(): Flow<List<DailyLogEntity>> = dailyLogDao.getAllFlowLogs()

    suspend fun getAllLogsSync(): List<DailyLogEntity> =
        dailyLogDao.getLogsBetweenSync(LocalDate.now().minusYears(2), LocalDate.now().plusDays(1))

    suspend fun getAllLogsWithSymptomsSync(): List<DailyLogWithSymptoms> =
        dailyLogDao.getAllLogsWithSymptomsSync()

    /**
     * Saves a daily log and its associated symptoms child records.
     * Recomputes cycle boundaries whenever flow data is saved.
     */
    suspend fun saveDailyLog(log: DailyLogEntity, symptoms: Map<String, Int> = emptyMap()) {
        dailyLogDao.insertOrUpdate(log)
        symptomLogDao.deleteSymptomsForDate(log.date)
        if (symptoms.isNotEmpty()) {
            val entities = symptoms.map { (tag, severity) ->
                SymptomLogEntity(
                    dailyLogId = log.date,
                    symptomTag = tag,
                    severityLevel = severity.coerceIn(1, 3)
                )
            }
            symptomLogDao.insertSymptoms(entities)
        }
        recalculateCyclesFromLogs()
    }

    suspend fun deleteDailyLog(date: LocalDate) {
        symptomLogDao.deleteSymptomsForDate(date)
        dailyLogDao.deleteLogForDate(date)
        recalculateCyclesFromLogs()
    }

    /**
     * Inspects historical flow logs, finds period starts (first day of bleeding
     * after >= 2 non-bleeding days), and updates the cycles table accordingly.
     */
    private suspend fun recalculateCyclesFromLogs() {
        val flowLogs = dailyLogDao.getAllFlowLogsSync().sortedBy { it.date }
        if (flowLogs.isEmpty()) {
            cycleDao.deleteAllCycles()
            return
        }

        // Group into distinct period clusters (days where bleeding occurred with <= 2 days gap)
        val periodClusters = mutableListOf<MutableList<LocalDate>>()
        var currentCluster = mutableListOf<LocalDate>()

        for (log in flowLogs) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(log.date)
            } else {
                val prevDate = currentCluster.last()
                val gap = ChronoUnit.DAYS.between(prevDate, log.date)
                if (gap <= 3) {
                    currentCluster.add(log.date)
                } else {
                    periodClusters.add(currentCluster)
                    currentCluster = mutableListOf(log.date)
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            periodClusters.add(currentCluster)
        }

        // Convert period clusters to CycleEntity records
        cycleDao.deleteAllCycles()
        for (i in periodClusters.indices) {
            val cluster = periodClusters[i]
            val startDate = cluster.first()
            val periodLength = (ChronoUnit.DAYS.between(startDate, cluster.last()) + 1).toInt().coerceAtLeast(1)

            val nextCluster = periodClusters.getOrNull(i + 1)
            val endDate = nextCluster?.first()?.minusDays(1)
            val cycleLength = if (nextCluster != null) {
                ChronoUnit.DAYS.between(startDate, nextCluster.first()).toInt()
            } else null

            cycleDao.insertCycle(
                CycleEntity(
                    startDate = startDate,
                    endDate = endDate,
                    periodLengthDays = periodLength,
                    cycleLengthDays = cycleLength
                )
            )
        }
        context?.let { ctx ->
            com.example.widget.CycleWidgetProvider.updateAllWidgets(ctx)
        }
    }

    /**
     * Combines cycles, logs, and settings into reactive cycle prediction state.
     * When there are no logged entries at all, currentCycleStartDate is null.
     * Once the user logs their first entry, currentCycleStartDate becomes non-null.
     */
    fun observePredictionState(today: LocalDate): Flow<CyclePredictionState> {
        return combine(
            cycleDao.getAllCycles(),
            dailyLogDao.getLogForDate(today),
            dailyLogDao.getAllDailyLogs(),
            dailyLogDao.getAllLogsWithSymptoms(),
            userSettingsDao.getSettings()
        ) { cycles, todayLog, allLogs, allLogsWithSymptoms, settings ->
            val isCurrentlyBleeding = todayLog?.flowIntensity != null && todayLog.flowIntensity != FlowIntensity.NONE
            val completedCycles = cycles.filter { it.cycleLengthDays != null }
            val completedCycleLengths = completedCycles.mapNotNull { it.cycleLengthDays }
            val periodDurations = completedCycles.map { it.periodLengthDays }

            val activeCycle = cycles.firstOrNull { it.endDate == null }
            val lastCompletedCycle = completedCycles.firstOrNull()

            // When user has not logged anything ever, currentCycleStartDate is strictly null.
            // Once they log their first entry, currentCycleStartDate becomes non-null.
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

            predictionEngine.calculatePrediction(
                today = today,
                currentCycleStartDate = cycleStartDate,
                completedCycleLengths = completedCycleLengths,
                historicalPeriodDurations = periodDurations,
                isCurrentlyBleeding = isCurrentlyBleeding,
                lastCycleEndDate = lastCompletedCycle?.endDate,
                allLoggedDates = allLoggedDates,
                stableSymptomMarker = stableMarker
            )
        }
    }

    /**
     * Observes statistical predictions (hurdle models for symptoms, 8 binary models for mood,
     * ordinal models for sleep and energy). Gated behind 3 completed cycles + 40 logged days.
     */
    fun observeStatisticalPredictions(today: LocalDate): Flow<StatisticalPredictions?> {
        return combine(
            cycleDao.getAllCycles(),
            dailyLogDao.getAllLogsWithSymptoms(),
            observePredictionState(today)
        ) { cycles, allLogs, predState ->
            val currentCycleDay = when (predState) {
                is CyclePredictionState.Predicted -> predState.currentCycleDay
                is CyclePredictionState.LearningCycle -> predState.currentCycleDay
                is CyclePredictionState.AwaitingNextCycle -> null
            }
            val predictedCycleLength = when (predState) {
                is CyclePredictionState.Predicted -> predState.predictedCycleLength
                is CyclePredictionState.AwaitingNextCycle -> predState.averageCycleLength
                is CyclePredictionState.LearningCycle -> predState.posteriorCycleLength.roundToInt()
            }
            statisticalPredictionEngine.computePredictions(
                today = today,
                currentCycleDay = currentCycleDay,
                predictedCycleLength = predictedCycleLength,
                cycles = cycles,
                allLogs = allLogs
            )
        }
    }

    suspend fun seedSampleCycles() {
        val today = LocalDate.now()
        // Clear existing data first
        symptomLogDao.deleteAllSymptoms()
        dailyLogDao.deleteAllDailyLogs()
        cycleDao.deleteAllCycles()

        // Cycle 1 (76 days ago): 5 days of bleeding
        for (offset in 0..4) {
            val date = today.minusDays((76 - offset).toLong())
            val intensity = when (offset) {
                0 -> FlowIntensity.MEDIUM
                1 -> FlowIntensity.HEAVY
                2 -> FlowIntensity.MEDIUM
                else -> FlowIntensity.LIGHT
            }
            dailyLogDao.insertOrUpdate(
                DailyLogEntity(
                    date = date,
                    flowIntensity = intensity,
                    mood = listOf("calm")
                )
            )
            if (offset <= 1) {
                symptomLogDao.insertSymptoms(
                    listOf(
                        SymptomLogEntity(
                            dailyLogId = date,
                            symptomTag = "cramps",
                            severityLevel = 2
                        )
                    )
                )
            }
        }

        // Cycle 2 (48 days ago): 5 days of bleeding
        for (offset in 0..4) {
            val date = today.minusDays((48 - offset).toLong())
            val intensity = when (offset) {
                0 -> FlowIntensity.MEDIUM
                1 -> FlowIntensity.HEAVY
                2 -> FlowIntensity.MEDIUM
                else -> FlowIntensity.LIGHT
            }
            dailyLogDao.insertOrUpdate(
                DailyLogEntity(
                    date = date,
                    flowIntensity = intensity,
                    mood = listOf("energetic")
                )
            )
            if (offset <= 1) {
                symptomLogDao.insertSymptoms(
                    listOf(
                        SymptomLogEntity(
                            dailyLogId = date,
                            symptomTag = "headache",
                            severityLevel = 2
                        )
                    )
                )
            }
        }

        // Current Cycle (started 20 days ago): Day 21 Luteal phase
        // 5 days of bleeding (days 20 to 16 ago)
        for (offset in 0..4) {
            val date = today.minusDays((20 - offset).toLong())
            val intensity = when (offset) {
                0 -> FlowIntensity.HEAVY
                1 -> FlowIntensity.HEAVY
                2 -> FlowIntensity.MEDIUM
                3 -> FlowIntensity.LIGHT
                else -> FlowIntensity.LIGHT
            }
            dailyLogDao.insertOrUpdate(
                DailyLogEntity(
                    date = date,
                    flowIntensity = intensity,
                    mood = listOf("sensitive")
                )
            )
            if (offset == 0) {
                symptomLogDao.insertSymptoms(
                    listOf(
                        SymptomLogEntity(
                            dailyLogId = date,
                            symptomTag = "cramps",
                            severityLevel = 2
                        )
                    )
                )
            }
        }

        // Mid-cycle and Luteal phase days without flow (to demonstrate logged entry dots)
        val nonFlowEntries = listOf(
            Triple(today.minusDays(10), listOf("energetic"), "bloating" to 1),
            Triple(today.minusDays(5), listOf("calm"), "acne" to 2),
            Triple(today.minusDays(3), listOf("sensitive"), "cramps" to 1),
            Triple(today.minusDays(1), listOf("tired"), "headache" to 2)
        )

        for ((date, moods, symptom) in nonFlowEntries) {
            dailyLogDao.insertOrUpdate(
                DailyLogEntity(
                    date = date,
                    flowIntensity = FlowIntensity.NONE,
                    mood = moods
                )
            )
            symptomLogDao.insertSymptoms(
                listOf(
                    SymptomLogEntity(
                        dailyLogId = date,
                        symptomTag = symptom.first,
                        severityLevel = symptom.second
                    )
                )
            )
        }

        recalculateCyclesFromLogs()
    }

    /**
     * Inserts past remembered period start dates as completed CycleEntities.
     * Enforces chronological order, computes cycle_length_days between consecutive entries,
     * seeds flow daily logs for period lengths, and transitions prediction engine out of cold-start.
     */
    suspend fun addPastCycles(entries: List<PastCycleInput>) {
        if (entries.isEmpty()) return
        val sorted = entries.sortedBy { it.startDate }
        val today = LocalDate.now()

        // 1. Seed flow logs for each remembered period
        for (entry in sorted) {
            val duration = entry.durationDays.coerceIn(1, 10)
            for (dayOffset in 0 until duration) {
                val logDate = entry.startDate.plusDays(dayOffset.toLong())
                val intensity = when (dayOffset) {
                    0 -> FlowIntensity.MEDIUM
                    1 -> FlowIntensity.HEAVY
                    2 -> FlowIntensity.MEDIUM
                    else -> FlowIntensity.LIGHT
                }
                dailyLogDao.insertOrUpdate(
                    DailyLogEntity(
                        date = logDate,
                        flowIntensity = intensity,
                        mood = listOf("calm")
                    )
                )
                if (dayOffset <= 1) {
                    symptomLogDao.insertSymptoms(
                        listOf(
                            SymptomLogEntity(
                                dailyLogId = logDate,
                                symptomTag = "cramps",
                                severityLevel = 2
                            )
                        )
                    )
                }
            }
        }

        // 2. Compute cycles
        val cyclesToInsert = mutableListOf<CycleEntity>()
        if (sorted.size >= 2) {
            for (i in 0 until sorted.size - 1) {
                val curr = sorted[i]
                val next = sorted[i + 1]
                val cycleLength = ChronoUnit.DAYS.between(curr.startDate, next.startDate).toInt().coerceAtLeast(1)
                val duration = curr.durationDays.coerceIn(1, 10)
                cyclesToInsert.add(
                    CycleEntity(
                        startDate = curr.startDate,
                        endDate = next.startDate.minusDays(1),
                        periodLengthDays = duration,
                        cycleLengthDays = cycleLength
                    )
                )
            }

            val lastEntry = sorted.last()
            val lastDuration = lastEntry.durationDays.coerceIn(1, 10)
            val avgCycleLength = cyclesToInsert.mapNotNull { it.cycleLengthDays }
                .takeIf { it.isNotEmpty() }?.average()?.toInt()?.coerceIn(21, 45) ?: 28

            if (cyclesToInsert.size < 2) {
                val expectedEnd = lastEntry.startDate.plusDays((avgCycleLength - 1).toLong())
                if (expectedEnd.isBefore(today)) {
                    cyclesToInsert.add(
                        CycleEntity(
                            startDate = lastEntry.startDate,
                            endDate = expectedEnd,
                            periodLengthDays = lastDuration,
                            cycleLengthDays = avgCycleLength
                        )
                    )
                    val openStart = expectedEnd.plusDays(1)
                    if (!openStart.isAfter(today)) {
                        cyclesToInsert.add(
                            CycleEntity(
                                startDate = openStart,
                                endDate = null,
                                periodLengthDays = lastDuration,
                                cycleLengthDays = null
                            )
                        )
                    }
                } else {
                    val first = sorted.first()
                    val priorStart = first.startDate.minusDays(avgCycleLength.toLong())
                    cyclesToInsert.add(
                        0,
                        CycleEntity(
                            startDate = priorStart,
                            endDate = first.startDate.minusDays(1),
                            periodLengthDays = first.durationDays.coerceIn(1, 10),
                            cycleLengthDays = avgCycleLength
                        )
                    )
                    cyclesToInsert.add(
                        CycleEntity(
                            startDate = lastEntry.startDate,
                            endDate = null,
                            periodLengthDays = lastDuration,
                            cycleLengthDays = null
                        )
                    )
                }
            } else {
                val expectedEnd = lastEntry.startDate.plusDays((avgCycleLength - 1).toLong())
                if (expectedEnd.isBefore(today)) {
                    cyclesToInsert.add(
                        CycleEntity(
                            startDate = lastEntry.startDate,
                            endDate = expectedEnd,
                            periodLengthDays = lastDuration,
                            cycleLengthDays = avgCycleLength
                        )
                    )
                    val openStart = expectedEnd.plusDays(1)
                    if (!openStart.isAfter(today)) {
                        cyclesToInsert.add(
                            CycleEntity(
                                startDate = openStart,
                                endDate = null,
                                periodLengthDays = lastDuration,
                                cycleLengthDays = null
                            )
                        )
                    }
                } else {
                    cyclesToInsert.add(
                        CycleEntity(
                            startDate = lastEntry.startDate,
                            endDate = null,
                            periodLengthDays = lastDuration,
                            cycleLengthDays = null
                        )
                    )
                }
            }
        } else if (sorted.size == 1) {
            val entry = sorted.first()
            val duration = entry.durationDays.coerceIn(1, 10)
            cyclesToInsert.add(
                CycleEntity(
                    startDate = entry.startDate,
                    endDate = null,
                    periodLengthDays = duration,
                    cycleLengthDays = null
                )
            )
        }

        cycleDao.deleteAllCycles()
        for (cycle in cyclesToInsert) {
            cycleDao.insertCycle(cycle)
        }
    }

    suspend fun clearAllData() {
        symptomLogDao.deleteAllSymptoms()
        dailyLogDao.deleteAllDailyLogs()
        cycleDao.deleteAllCycles()
        profileDao.clearProfile()
    }

    suspend fun simulateTodayLog(
        flow: FlowIntensity = FlowIntensity.MEDIUM,
        symptoms: List<Pair<String, Int>> = listOf("cramps" to 3, "bloating" to 2, "fatigue" to 2),
        moods: List<String> = listOf("tired", "calm"),
        energy: EnergyLevel = EnergyLevel.LOW,
        sleep: SleepQuality = SleepQuality.FAIR,
        bbt: Double? = 36.4,
        notes: String = "Logged via Developer Testing"
    ) {
        val today = LocalDate.now()
        dailyLogDao.insertOrUpdate(
            DailyLogEntity(
                date = today,
                flowIntensity = flow,
                mood = moods,
                energyLevel = energy,
                sleepQuality = sleep,
                bbtCelsius = bbt,
                notes = notes,
                sexualActivity = SexualActivity.PROTECTED,
                medicationTaken = true
            )
        )
        symptomLogDao.deleteSymptomsForDate(today)
        symptomLogDao.insertSymptoms(
            symptoms.map { (tag, severity) ->
                SymptomLogEntity(
                    dailyLogId = today,
                    symptomTag = tag,
                    severityLevel = severity
                )
            }
        )
        recalculateCyclesFromLogs()
    }

    suspend fun seedRichCycles() {
        clearAllData()
        val today = LocalDate.now()
        profileDao.insertOrUpdateProfile(
            ProfileEntity(
                name = "Palkin",
                onboardingCompletedAt = System.currentTimeMillis() - (180L * 24 * 3600 * 1000)
            )
        )

        // Seed 5 consecutive past cycles: lengths 28, 29, 27, 28, 29
        val cycleLengths = listOf(28, 29, 27, 28, 29)
        var curStart = today.minusDays(cycleLengths.sum().toLong() - 14)
        val cycleEntities = mutableListOf<CycleEntity>()

        for ((idx, length) in cycleLengths.withIndex()) {
            val periodLength = if (idx % 2 == 0) 5 else 4
            val isCurrentCycle = (idx == cycleLengths.lastIndex)
            val endDate = if (isCurrentCycle) null else curStart.plusDays(length.toLong() - 1)

            cycleEntities.add(
                CycleEntity(
                    startDate = curStart,
                    endDate = endDate,
                    periodLengthDays = periodLength,
                    cycleLengthDays = if (isCurrentCycle) null else length
                )
            )

            // Seed detailed daily logs for each cycle
            val daysToLog = if (isCurrentCycle) 14 else length
            for (day in 0 until daysToLog) {
                val logDate = curStart.plusDays(day.toLong())
                if (logDate.isAfter(today)) break

                val flow = when {
                    day == 0 -> FlowIntensity.MEDIUM
                    day == 1 -> FlowIntensity.HEAVY
                    day == 2 -> FlowIntensity.MEDIUM
                    day < periodLength -> FlowIntensity.LIGHT
                    else -> FlowIntensity.NONE
                }

                val moods = when {
                    day < periodLength -> listOf("calm", "tired")
                    day in (periodLength..13) -> listOf("energetic", "happy")
                    day in (14..16) -> listOf("calm", "happy")
                    else -> listOf("anxious", "irritable")
                }

                val energy = when {
                    day < periodLength -> EnergyLevel.LOW
                    day in (periodLength..13) -> EnergyLevel.HIGH
                    day in (14..16) -> EnergyLevel.PEAK
                    else -> EnergyLevel.BALANCED
                }

                val sleep = when {
                    day < periodLength -> SleepQuality.FAIR
                    day in (periodLength..16) -> SleepQuality.GOOD
                    else -> SleepQuality.POOR
                }

                val bbt = when {
                    day < 14 -> 36.35 + (day % 3) * 0.05
                    else -> 36.78 + (day % 3) * 0.04
                }

                dailyLogDao.insertOrUpdate(
                    DailyLogEntity(
                        date = logDate,
                        flowIntensity = flow,
                        mood = moods,
                        energyLevel = energy,
                        sleepQuality = sleep,
                        notes = if (day == 14) "Peak energy and focus today" else if (day == 1) "Mild morning cramps, staying hydrated" else "",
                        bbtCelsius = bbt,
                        sexualActivity = if (day == 13 || day == 14) SexualActivity.PROTECTED else SexualActivity.NONE,
                        medicationTaken = (day <= 1)
                    )
                )

                val symptoms = mutableListOf<SymptomLogEntity>()
                if (day <= 2) {
                    symptoms.add(SymptomLogEntity(dailyLogId = logDate, symptomTag = "cramps", severityLevel = if (day == 1) 3 else 2))
                    symptoms.add(SymptomLogEntity(dailyLogId = logDate, symptomTag = "fatigue", severityLevel = 2))
                }
                if (day in 12..15) {
                    symptoms.add(SymptomLogEntity(dailyLogId = logDate, symptomTag = "bloating", severityLevel = 1))
                }
                if (day > 20) {
                    symptoms.add(SymptomLogEntity(dailyLogId = logDate, symptomTag = "bloating", severityLevel = 2))
                    symptoms.add(SymptomLogEntity(dailyLogId = logDate, symptomTag = "tender_breasts", severityLevel = 2))
                }
                if (symptoms.isNotEmpty()) {
                    symptomLogDao.insertSymptoms(symptoms)
                }
            }

            curStart = curStart.plusDays(length.toLong())
        }

        cycleDao.deleteAllCycles()
        for (c in cycleEntities) {
            cycleDao.insertCycle(c)
        }
    }

    suspend fun seedSampleDataIfEmpty() {
        // Left empty intentionally to support clean first-time onboarding
    }

    suspend fun exportEncryptedBackupToUri(context: Context, uri: Uri, passphrase: String): Result<BackupMetadata> {
        val manager = backupManager ?: return Result.failure(IllegalStateException("Backup manager not initialized"))
        val os = context.contentResolver.openOutputStream(uri)
            ?: return Result.failure(IllegalStateException("Unable to access the chosen file destination."))
        return manager.exportToOutputStream(os, passphrase)
    }

    suspend fun createShareableBackup(context: Context, passphrase: String): Result<Pair<Uri, BackupMetadata>> {
        val manager = backupManager ?: return Result.failure(IllegalStateException("Backup manager not initialized"))
        return manager.createShareableBackup(context, passphrase)
    }

    suspend fun restoreEncryptedBackupFromUri(context: Context, uri: Uri, passphrase: String): Result<BackupRestoreResult> {
        val manager = backupManager ?: return Result.failure(IllegalStateException("Backup manager not initialized"))
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Unable to read the chosen backup file."))
        return manager.restoreDatabase(inputStream, passphrase)
    }

    suspend fun inspectEncryptedBackupFromUri(context: Context, uri: Uri, passphrase: String): Result<BackupMetadata> {
        val manager = backupManager ?: return Result.failure(IllegalStateException("Backup manager not initialized"))
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Unable to read the chosen backup file."))
        return manager.inspectBackup(inputStream, passphrase)
    }

    suspend fun buildDoctorReportData(rangeMonths: Int? = 6): DoctorReportData {
        return doctorReportGenerator.buildReportData(rangeMonths)
    }

    suspend fun createShareableDoctorReport(
        context: Context,
        format: ReportFormat,
        rangeMonths: Int? = 6
    ): Result<Pair<Uri, String>> {
        return doctorReportGenerator.createShareableReport(context, format, rangeMonths)
    }

    suspend fun exportDoctorReportToUri(
        context: Context,
        uri: Uri,
        format: ReportFormat,
        rangeMonths: Int? = 6
    ): Result<String> {
        val os = context.contentResolver.openOutputStream(uri)
            ?: return Result.failure(IllegalStateException("Unable to open output file destination."))
        return doctorReportGenerator.exportReportToOutputStream(os, format, rangeMonths)
    }
}
