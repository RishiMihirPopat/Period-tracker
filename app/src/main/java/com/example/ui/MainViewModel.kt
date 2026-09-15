package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupMetadata
import com.example.data.backup.BackupRestoreResult
import com.example.data.report.ReportFormat
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.local.entity.ProfileEntity
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.PastCycleInput
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import com.example.data.repository.CycleRepository
import com.example.domain.CyclePredictionState
import com.example.util.CycleNotificationReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

import com.example.domain.CyclePhase
import com.example.domain.CycleStats
import com.example.domain.CycleStatsEngine
import com.example.domain.PhaseExpectation
import com.example.domain.WhatToExpectEngine
import com.example.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.combine

enum class AppScreen {
    TODAY,
    CALENDAR,
    INSIGHTS,
    SETTINGS,
    PAST_CYCLES,
    ONBOARDING
}

data class LogDraft(
    val date: LocalDate,
    val flowIntensity: FlowIntensity = FlowIntensity.NONE,
    val symptoms: Map<String, Int> = emptyMap(), // symptomTag -> severityLevel (1..5)
    val mood: Set<String> = emptySet(),
    val energyLevel: EnergyLevel? = null,
    val sleepQuality: SleepQuality? = null,
    val notes: String = "",
    val bbtCelsius: Double? = null,
    val sexualActivity: SexualActivity? = null,
    val medicationTaken: Boolean? = null
)

class MainViewModel(
    private val repository: CycleRepository,
    private val context: Context? = null
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(AppScreen.TODAY)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    private var previousScreen: AppScreen = AppScreen.TODAY

    val profile: StateFlow<ProfileEntity?> =
        repository.getProfile()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private var lastGreetingShownDate: LocalDate? = null
    private val _showDailyGreeting = MutableStateFlow(false)
    val showDailyGreeting: StateFlow<Boolean> = _showDailyGreeting.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    // Prediction state based on today
    val predictionState: StateFlow<CyclePredictionState> =
        repository.observePredictionState(LocalDate.now())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CyclePredictionState.LearningCycle(
                    completedCyclesCount = 0,
                    currentCycleDay = null,
                    isActivelyBleeding = false
                )
            )

    // Today's log
    val todayLog: StateFlow<DailyLogEntity?> =
        repository.getLogForDate(LocalDate.now())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // Logs for selected calendar month
    val monthLogs: StateFlow<List<DailyLogWithSymptoms>> =
        _currentMonth.flatMapLatest { ym ->
            val start = ym.atDay(1).minusDays(7)
            val end = ym.atEndOfMonth().plusDays(7)
            repository.getLogsWithSymptomsBetween(start, end)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val cycles: StateFlow<List<CycleEntity>> =
        repository.getAllCycles()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Log draft for the Log Entry screen
    private val _logDraft = MutableStateFlow(LogDraft(LocalDate.now()))
    val logDraft: StateFlow<LogDraft> = _logDraft.asStateFlow()

    // Bottom sheet state for Log Flow & Day Detail
    private val _showLogSheet = MutableStateFlow(false)
    val showLogSheet: StateFlow<Boolean> = _showLogSheet.asStateFlow()

    private val _showDayDetailSheet = MutableStateFlow(false)
    val showDayDetailSheet: StateFlow<Boolean> = _showDayDetailSheet.asStateFlow()

    private val _dayDetailDate = MutableStateFlow<LocalDate?>(null)
    val dayDetailDate: StateFlow<LocalDate?> = _dayDetailDate.asStateFlow()

    private val _dayDetailLog = MutableStateFlow<DailyLogWithSymptoms?>(null)
    val dayDetailLog: StateFlow<DailyLogWithSymptoms?> = _dayDetailLog.asStateFlow()

    // Settings state
    val userSettings: StateFlow<UserSettingsEntity> =
        repository.getUserSettings()
            .flatMapLatest { settings ->
                kotlinx.coroutines.flow.flowOf(settings ?: UserSettingsEntity())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UserSettingsEntity()
            )

    // App Lock State
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun unlockApp() {
        _isUnlocked.value = true
    }

    // Phase Expectation engine
    private val whatToExpectEngine = WhatToExpectEngine()
    private val cycleStatsEngine = CycleStatsEngine()

    // Phase Expectation reactive state:
    // Null when currentCycleStartDate is null (user hasn't logged anything ever).
    val phaseExpectation: StateFlow<PhaseExpectation?> =
        combine(
            predictionState,
            repository.getAllLogsWithSymptoms(),
            repository.getAllCycles(),
            repository.observeStatisticalPredictions(LocalDate.now())
        ) { pred, allLogs, cycles, statPreds ->
            if (pred.currentCycleStartDate == null) {
                null
            } else {
                val phase = when (pred) {
                    is CyclePredictionState.Predicted -> pred.currentPhase
                    is CyclePredictionState.LearningCycle -> if (pred.isActivelyBleeding) CyclePhase.MENSTRUAL else CyclePhase.FOLLICULAR
                    is CyclePredictionState.AwaitingNextCycle -> CyclePhase.FOLLICULAR
                }
                val day = when (pred) {
                    is CyclePredictionState.Predicted -> pred.currentCycleDay
                    is CyclePredictionState.LearningCycle -> pred.currentCycleDay
                    is CyclePredictionState.AwaitingNextCycle -> null
                }
                whatToExpectEngine.getExpectation(
                    phase = phase,
                    cycleDay = day,
                    allLogs = allLogs,
                    cycles = cycles,
                    today = LocalDate.now(),
                    statisticalPredictions = statPreds
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Insights and stats
    val cycleStats: StateFlow<CycleStats> =
        combine(repository.getAllCycles(), repository.getAllLogsWithSymptoms()) { cycles, allLogs ->
            cycleStatsEngine.calculateStats(cycles, allLogs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = cycleStatsEngine.calculateStats(emptyList(), emptyList())
        )

    init {
        loadLogForDate(LocalDate.now())
        viewModelScope.launch {
            repository.getProfile().collect { p ->
                if (p == null || p.onboardingCompletedAt == null) {
                    _currentScreen.value = AppScreen.ONBOARDING
                } else {
                    checkAndTriggerDailyGreeting()
                }
            }
        }
        viewModelScope.launch {
            combine(userSettings, predictionState) { settings, pred ->
                Pair(settings, pred)
            }.collect { (settings, pred) ->
                context?.let { ctx ->
                    CycleNotificationReceiver.scheduleAllAlerts(ctx, pred, settings)
                }
            }
        }
    }

    fun checkAndTriggerDailyGreeting() {
        val today = LocalDate.now()
        val userProfile = profile.value
        if (userProfile != null && userProfile.name.isNotBlank() && lastGreetingShownDate != today) {
            _showDailyGreeting.value = true
            lastGreetingShownDate = today
        }
    }

    fun dismissDailyGreeting() {
        _showDailyGreeting.value = false
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value == AppScreen.TODAY && screen != AppScreen.TODAY) {
            _showDailyGreeting.value = false
        }
        if (screen == AppScreen.SETTINGS || screen == AppScreen.PAST_CYCLES) {
            if (_currentScreen.value != AppScreen.SETTINGS && _currentScreen.value != AppScreen.PAST_CYCLES) {
                previousScreen = _currentScreen.value
            }
        }
        _currentScreen.value = screen
    }

    fun navigateBack() {
        if (_currentScreen.value == AppScreen.PAST_CYCLES) {
            _currentScreen.value = AppScreen.SETTINGS
        } else if (_currentScreen.value == AppScreen.SETTINGS) {
            _currentScreen.value = if (previousScreen != AppScreen.SETTINGS && previousScreen != AppScreen.PAST_CYCLES) {
                previousScreen
            } else {
                AppScreen.TODAY
            }
        } else {
            _currentScreen.value = AppScreen.TODAY
        }
    }

    fun completeOnboarding(name: String, pastCycles: List<PastCycleInput>) {
        viewModelScope.launch {
            repository.completeOnboarding(name)
            if (pastCycles.isNotEmpty()) {
                repository.addPastCycles(pastCycles)
            }
            _currentScreen.value = AppScreen.TODAY
            checkAndTriggerDailyGreeting()
        }
    }

    fun savePastCycles(pastCycles: List<PastCycleInput>) {
        viewModelScope.launch {
            repository.addPastCycles(pastCycles)
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadLogForDate(date)
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
    }

    fun prevMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }

    fun openLogFlow(date: LocalDate = LocalDate.now()) {
        _selectedDate.value = date
        loadLogForDate(date)
        _showLogSheet.value = true
    }

    fun closeLogFlow() {
        _showLogSheet.value = false
    }

    fun openDayDetail(date: LocalDate) {
        _dayDetailDate.value = date
        viewModelScope.launch {
            repository.getLogWithSymptoms(date).collect { log ->
                _dayDetailLog.value = log
            }
        }
        _showDayDetailSheet.value = true
    }

    fun closeDayDetail() {
        _showDayDetailSheet.value = false
        _dayDetailDate.value = null
    }

    fun openLogForDate(date: LocalDate) {
        openLogFlow(date)
    }

    fun updateFlow(flow: FlowIntensity) {
        _logDraft.value = _logDraft.value.copy(flowIntensity = flow)
    }

    fun toggleSymptom(symptomId: String) {
        val current = _logDraft.value.symptoms.toMutableMap()
        if (current.containsKey(symptomId)) {
            current.remove(symptomId)
        } else {
            current[symptomId] = 3 // Default Moderate (3 on 1..5 scale)
        }
        _logDraft.value = _logDraft.value.copy(symptoms = current)
    }

    fun updateSymptomSeverity(symptomId: String, severity: Int) {
        val current = _logDraft.value.symptoms.toMutableMap()
        current[symptomId] = severity.coerceIn(1, 5)
        _logDraft.value = _logDraft.value.copy(symptoms = current)
    }

    fun toggleMood(moodId: String) {
        val current = _logDraft.value.mood.toMutableSet()
        if (current.contains(moodId)) current.remove(moodId) else current.add(moodId)
        _logDraft.value = _logDraft.value.copy(mood = current)
    }

    fun updateEnergyLevel(energy: EnergyLevel?) {
        _logDraft.value = _logDraft.value.copy(energyLevel = energy)
    }

    fun updateSleepQuality(sleep: SleepQuality?) {
        _logDraft.value = _logDraft.value.copy(sleepQuality = sleep)
    }

    fun updateNotes(notes: String) {
        _logDraft.value = _logDraft.value.copy(notes = notes)
    }

    fun updateSexualActivity(activity: SexualActivity?) {
        _logDraft.value = _logDraft.value.copy(sexualActivity = activity)
    }

    fun updateMedication(taken: Boolean?) {
        _logDraft.value = _logDraft.value.copy(medicationTaken = taken)
    }

    private fun loadLogForDate(date: LocalDate) {
        viewModelScope.launch {
            repository.getLogWithSymptoms(date).collect { logWithSymptoms ->
                if (logWithSymptoms != null) {
                    val log = logWithSymptoms.log
                    val symMap = logWithSymptoms.symptoms.associate { it.symptomTag to it.severityLevel }
                    _logDraft.value = LogDraft(
                        date = log.date,
                        flowIntensity = log.flowIntensity,
                        symptoms = symMap,
                        mood = log.mood.toSet(),
                        energyLevel = log.energyLevel,
                        sleepQuality = log.sleepQuality,
                        notes = log.notes,
                        bbtCelsius = log.bbtCelsius,
                        sexualActivity = log.sexualActivity,
                        medicationTaken = log.medicationTaken
                    )
                } else {
                    _logDraft.value = LogDraft(date = date)
                }
            }
        }
    }

    fun saveDraft(onSaved: () -> Unit = {}) {
        val draft = _logDraft.value
        viewModelScope.launch {
            repository.saveDailyLog(
                DailyLogEntity(
                    date = draft.date,
                    flowIntensity = draft.flowIntensity,
                    mood = draft.mood.toList(),
                    energyLevel = draft.energyLevel,
                    sleepQuality = draft.sleepQuality,
                    notes = draft.notes,
                    bbtCelsius = draft.bbtCelsius,
                    sexualActivity = draft.sexualActivity,
                    medicationTaken = draft.medicationTaken
                ),
                symptoms = draft.symptoms
            )
            onSaved()
        }
    }

    fun updateLutealPhase(days: Int) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.updateSettings(current.copy(lutealPhaseLengthDefault = days))
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(notificationsEnabled = enabled)
            repository.updateSettings(updated)
            context?.let { ctx ->
                CycleNotificationReceiver.scheduleAllAlerts(ctx, predictionState.value, updated)
            }
        }
    }

    fun toggleFertileAlert(enabled: Boolean) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(fertileWindowAlertEnabled = enabled)
            repository.updateSettings(updated)
            context?.let { ctx ->
                CycleNotificationReceiver.scheduleAllAlerts(ctx, predictionState.value, updated)
            }
        }
    }

    fun toggleDailyDigest(enabled: Boolean) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(dailyDigestAlertEnabled = enabled)
            repository.updateSettings(updated)
            context?.let { ctx ->
                CycleNotificationReceiver.scheduleAllAlerts(ctx, predictionState.value, updated)
            }
        }
    }

    fun togglePhaseChangeAlert(enabled: Boolean) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(phaseChangeAlertEnabled = enabled)
            repository.updateSettings(updated)
            context?.let { ctx ->
                CycleNotificationReceiver.scheduleAllAlerts(ctx, predictionState.value, updated)
            }
        }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.updateSettings(current.copy(pinHash = pin))
            if (pin == null) {
                _isUnlocked.value = true
            }
        }
    }

    fun updateAccentColor(accent: String) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.updateSettings(current.copy(accentColor = accent))
        }
    }

    fun clearLogForDate(date: LocalDate) {
        viewModelScope.launch {
            repository.deleteDailyLog(date)
            _logDraft.value = LogDraft(date = date)
        }
    }

    fun seedSampleCycles() {
        viewModelScope.launch {
            repository.seedSampleCycles()
            loadLogForDate(LocalDate.now())
        }
    }

    fun seedRichCycles() {
        viewModelScope.launch {
            repository.seedRichCycles()
            loadLogForDate(LocalDate.now())
        }
    }

    fun simulateTodayLog() {
        viewModelScope.launch {
            repository.simulateTodayLog()
            loadLogForDate(LocalDate.now())
        }
    }

    fun triggerDailyGreetingTest() {
        _showDailyGreeting.value = true
    }

    fun testLockScreen() {
        if (!userSettings.value.pinHash.isNullOrBlank()) {
            _isUnlocked.value = false
        }
    }

    fun sendTestNotification(title: String, message: String) {
        context?.let { ctx ->
            CycleNotificationReceiver.sendImmediateNotification(
                context = ctx,
                title = title,
                message = message
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            loadLogForDate(LocalDate.now())
        }
    }

    // Encrypted Backup & Restore
    private val _backupOperationState = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val backupOperationState: StateFlow<BackupOperationState> = _backupOperationState.asStateFlow()

    fun clearBackupOperationState() {
        _backupOperationState.value = BackupOperationState.Idle
    }

    fun exportEncryptedBackupToUri(uri: Uri, passphrase: String) {
        val ctx = context ?: return
        viewModelScope.launch {
            _backupOperationState.value = BackupOperationState.Processing("Encrypting and saving backup...")
            val result = repository.exportEncryptedBackupToUri(ctx, uri, passphrase)
            result.fold(
                onSuccess = { metadata ->
                    _backupOperationState.value = BackupOperationState.ExportSuccess(
                        summary = metadata,
                        message = "Encrypted backup saved successfully (${metadata.cyclesCount} cycles, ${metadata.logsCount} daily logs)."
                    )
                },
                onFailure = { error ->
                    _backupOperationState.value = BackupOperationState.Error(
                        error.message ?: "Failed to export backup."
                    )
                }
            )
        }
    }

    fun createShareableBackup(passphrase: String, onUriReady: (Uri) -> Unit) {
        val ctx = context ?: return
        viewModelScope.launch {
            _backupOperationState.value = BackupOperationState.Processing("Encrypting backup for sharing...")
            val result = repository.createShareableBackup(ctx, passphrase)
            result.fold(
                onSuccess = { (uri, metadata) ->
                    _backupOperationState.value = BackupOperationState.ExportSuccess(
                        summary = metadata,
                        message = "Backup encrypted (${metadata.cyclesCount} cycles, ${metadata.logsCount} daily logs)."
                    )
                    onUriReady(uri)
                },
                onFailure = { error ->
                    _backupOperationState.value = BackupOperationState.Error(
                        error.message ?: "Failed to prepare shareable backup."
                    )
                }
            )
        }
    }

    fun restoreEncryptedBackupFromUri(uri: Uri, passphrase: String) {
        val ctx = context ?: return
        viewModelScope.launch {
            _backupOperationState.value = BackupOperationState.Processing("Decrypting and restoring data...")
            val result = repository.restoreEncryptedBackupFromUri(ctx, uri, passphrase)
            result.fold(
                onSuccess = { restoreResult ->
                    loadLogForDate(LocalDate.now())
                    _backupOperationState.value = BackupOperationState.RestoreSuccess(
                        result = restoreResult,
                        message = "Restored ${restoreResult.cyclesRestored} cycles and ${restoreResult.logsRestored} daily logs successfully."
                    )
                },
                onFailure = { error ->
                    _backupOperationState.value = BackupOperationState.Error(
                        error.message ?: "Failed to restore backup."
                    )
                }
            )
        }
    }

    // Doctor-Visit Health Report
    private val _doctorReportState = MutableStateFlow<DoctorReportOperationState>(DoctorReportOperationState.Idle)
    val doctorReportState: StateFlow<DoctorReportOperationState> = _doctorReportState.asStateFlow()

    fun clearDoctorReportState() {
        _doctorReportState.value = DoctorReportOperationState.Idle
    }

    fun exportDoctorReportToUri(uri: Uri, format: ReportFormat, rangeMonths: Int?) {
        val ctx = context ?: return
        viewModelScope.launch {
            _doctorReportState.value = DoctorReportOperationState.Generating("Generating ${format.displayName}...")
            val result = repository.exportDoctorReportToUri(ctx, uri, format, rangeMonths)
            result.fold(
                onSuccess = {
                    _doctorReportState.value = DoctorReportOperationState.Success(
                        "Doctor report saved (${format.displayName})."
                    )
                },
                onFailure = { error ->
                    _doctorReportState.value = DoctorReportOperationState.Error(
                        error.message ?: "Failed to save doctor report."
                    )
                }
            )
        }
    }

    fun createShareableDoctorReport(format: ReportFormat, rangeMonths: Int?, onUriReady: (Uri, String) -> Unit) {
        val ctx = context ?: return
        viewModelScope.launch {
            _doctorReportState.value = DoctorReportOperationState.Generating("Preparing ${format.displayName} for sharing...")
            val result = repository.createShareableDoctorReport(ctx, format, rangeMonths)
            result.fold(
                onSuccess = { (uri, fileName) ->
                    _doctorReportState.value = DoctorReportOperationState.Success(
                        "Report ready: $fileName"
                    )
                    onUriReady(uri, fileName)
                },
                onFailure = { error ->
                    _doctorReportState.value = DoctorReportOperationState.Error(
                        error.message ?: "Failed to prepare report for sharing."
                    )
                }
            )
        }
    }
}

sealed class BackupOperationState {
    object Idle : BackupOperationState()
    data class Processing(val message: String) : BackupOperationState()
    data class ExportSuccess(val summary: BackupMetadata, val message: String) : BackupOperationState()
    data class RestoreSuccess(val result: BackupRestoreResult, val message: String) : BackupOperationState()
    data class Error(val message: String) : BackupOperationState()
}

sealed class DoctorReportOperationState {
    object Idle : DoctorReportOperationState()
    data class Generating(val message: String) : DoctorReportOperationState()
    data class Success(val message: String) : DoctorReportOperationState()
    data class Error(val message: String) : DoctorReportOperationState()
}
