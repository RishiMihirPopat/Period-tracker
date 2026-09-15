package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.report.ReportFormat
import com.example.ui.components.DayDetailBottomSheet
import com.example.ui.components.DoctorReportExportDialog
import com.example.ui.components.LogBottomSheet
import com.example.ui.components.PinLockScreen
import com.example.ui.components.auraBackgroundBrush
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PastCyclesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import java.time.LocalDate

@Composable
fun MainAppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val showDailyGreeting by viewModel.showDailyGreeting.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val predictionState by viewModel.predictionState.collectAsStateWithLifecycle()
    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val monthLogs by viewModel.monthLogs.collectAsStateWithLifecycle()
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val logDraft by viewModel.logDraft.collectAsStateWithLifecycle()
    val phaseExpectation by viewModel.phaseExpectation.collectAsStateWithLifecycle()
    val cycleStats by viewModel.cycleStats.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val backupOperationState by viewModel.backupOperationState.collectAsStateWithLifecycle()
    val doctorReportState by viewModel.doctorReportState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showDoctorReportDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<ReportFormat?>(null) }
    var pendingExportRangeMonths by remember { mutableStateOf<Int?>(null) }

    val saveReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/*")
    ) { uri: Uri? ->
        if (uri != null && pendingExportFormat != null) {
            viewModel.exportDoctorReportToUri(uri, pendingExportFormat!!, pendingExportRangeMonths)
        }
    }

    val showLogSheet by viewModel.showLogSheet.collectAsStateWithLifecycle()
    val showDayDetailSheet by viewModel.showDayDetailSheet.collectAsStateWithLifecycle()
    val dayDetailDate by viewModel.dayDetailDate.collectAsStateWithLifecycle()
    val dayDetailLog by viewModel.dayDetailLog.collectAsStateWithLifecycle()

    // Enforce App Lock if PIN is configured and user has not yet unlocked
    if (userSettings.pinHash != null && !isUnlocked) {
        PinLockScreen(
            correctPin = userSettings.pinHash!!,
            onUnlocked = { viewModel.unlockApp() },
            modifier = modifier
        )
        return
    }

    val showBottomBar = currentScreen != AppScreen.PAST_CYCLES &&
            currentScreen != AppScreen.ONBOARDING

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.height(64.dp)
                    ) {
                        val navItemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = Color.Transparent
                        )

                        // 1. Today Tab
                        val isTodaySelected = currentScreen == AppScreen.TODAY
                        NavigationBarItem(
                            selected = isTodaySelected,
                            onClick = { viewModel.navigateTo(AppScreen.TODAY) },
                            icon = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Today,
                                        contentDescription = "Today",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isTodaySelected) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    "Today",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isTodaySelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_today")
                        )

                        // 2. Calendar Tab
                        val isCalendarSelected = currentScreen == AppScreen.CALENDAR
                        NavigationBarItem(
                            selected = isCalendarSelected,
                            onClick = { viewModel.navigateTo(AppScreen.CALENDAR) },
                            icon = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "Calendar",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isCalendarSelected) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    "Calendar",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isCalendarSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_calendar")
                        )

                        // 3. Insights Tab
                        val isInsightsSelected = currentScreen == AppScreen.INSIGHTS
                        NavigationBarItem(
                            selected = isInsightsSelected,
                            onClick = { viewModel.navigateTo(AppScreen.INSIGHTS) },
                            icon = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Analytics,
                                        contentDescription = "Insights",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isInsightsSelected) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    "Insights",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isInsightsSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_insights")
                        )

                        // 4. Settings Tab
                        val isSettingsSelected = currentScreen == AppScreen.SETTINGS
                        NavigationBarItem(
                            selected = isSettingsSelected,
                            onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                            icon = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isSettingsSelected) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    "Settings",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSettingsSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                }
            }
        },
        modifier = modifier.background(auraBackgroundBrush())
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(auraBackgroundBrush())
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val initialOrder = when (initialState) {
                        AppScreen.ONBOARDING -> 0
                        AppScreen.TODAY -> 1
                        AppScreen.CALENDAR -> 2
                        AppScreen.INSIGHTS -> 3
                        AppScreen.SETTINGS -> 4
                        AppScreen.PAST_CYCLES -> 5
                    }
                    val targetOrder = when (targetState) {
                        AppScreen.ONBOARDING -> 0
                        AppScreen.TODAY -> 1
                        AppScreen.CALENDAR -> 2
                        AppScreen.INSIGHTS -> 3
                        AppScreen.SETTINGS -> 4
                        AppScreen.PAST_CYCLES -> 5
                    }

                    if (targetState == AppScreen.PAST_CYCLES) {
                        (slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(240))) togetherWith (slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(200)))
                    } else if (initialState == AppScreen.PAST_CYCLES) {
                        (slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(240))) togetherWith (slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(200)))
                    } else if (targetOrder > initialOrder) {
                        (slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(220))) togetherWith (slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(180)))
                    } else {
                        (slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(220))) togetherWith (slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(180)))
                    }
                },
                label = "ScreenTransition",
                modifier = Modifier.fillMaxSize()
            ) { screen ->
                when (screen) {
                    AppScreen.ONBOARDING -> {
                        OnboardingScreen(
                            onComplete = { name, pastCycles ->
                                viewModel.completeOnboarding(name, pastCycles)
                            }
                        )
                    }

                    AppScreen.TODAY -> {
                        TodayScreen(
                            predictionState = predictionState,
                            todayLog = todayLog,
                            expectation = phaseExpectation,
                            onOpenLog = { date -> viewModel.openLogFlow(date) },
                            onOpenSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
                            userName = profile?.name,
                            showDailyGreeting = showDailyGreeting
                        )
                    }

                    AppScreen.CALENDAR -> {
                        CalendarScreen(
                            currentMonth = currentMonth,
                            selectedDate = selectedDate,
                            monthLogs = monthLogs,
                            predictionState = predictionState,
                            cycles = cycles,
                            onDayClick = { date -> viewModel.openDayDetail(date) },
                            onPrevMonth = { viewModel.prevMonth() },
                            onNextMonth = { viewModel.nextMonth() }
                        )
                    }

                    AppScreen.INSIGHTS -> {
                        InsightsScreen(
                            stats = cycleStats,
                            predictionState = predictionState,
                            expectation = phaseExpectation,
                            onOpenDoctorReportDialog = { showDoctorReportDialog = true }
                        )
                    }

                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            settings = userSettings,
                            onUpdateLutealPhase = { days -> viewModel.updateLutealPhase(days) },
                            onToggleNotifications = { enabled -> viewModel.toggleNotifications(enabled) },
                            onToggleFertileAlert = { enabled -> viewModel.toggleFertileAlert(enabled) },
                            onToggleDailyDigest = { enabled -> viewModel.toggleDailyDigest(enabled) },
                            onTogglePhaseChangeAlert = { enabled -> viewModel.togglePhaseChangeAlert(enabled) },
                            onSetPin = { pin -> viewModel.setPin(pin) },
                            onBack = { viewModel.navigateBack() },
                            onSeedSampleCycles = { viewModel.seedSampleCycles() },
                            onSeedRichCycles = { viewModel.seedRichCycles() },
                            onSimulateTodayLog = { viewModel.simulateTodayLog() },
                            onTriggerDailyGreeting = { viewModel.triggerDailyGreetingTest() },
                            onTestLockScreen = { viewModel.testLockScreen() },
                            onSendDummyNotification = { title, message -> viewModel.sendTestNotification(title, message) },
                            onClearAllData = { viewModel.clearAllData() },
                            onOpenAddPastCycles = { viewModel.navigateTo(AppScreen.PAST_CYCLES) },
                            onUpdateAccent = { accent -> viewModel.updateAccentColor(accent) },
                            backupOperationState = backupOperationState,
                            onExportBackup = { uri, passphrase -> viewModel.exportEncryptedBackupToUri(uri, passphrase) },
                            onShareBackup = { passphrase, onUriReady -> viewModel.createShareableBackup(passphrase, onUriReady) },
                            onRestoreBackup = { uri, passphrase -> viewModel.restoreEncryptedBackupFromUri(uri, passphrase) },
                            onClearBackupState = { viewModel.clearBackupOperationState() },
                            doctorReportState = doctorReportState,
                            onOpenDoctorReportDialog = { showDoctorReportDialog = true },
                            onClearDoctorReportState = { viewModel.clearDoctorReportState() }
                        )
                    }

                    AppScreen.PAST_CYCLES -> {
                        PastCyclesScreen(
                            onBack = { viewModel.navigateBack() },
                            onSavePastCycles = { pastCycles ->
                                viewModel.savePastCycles(pastCycles)
                            }
                        )
                    }
                }
            }

            // Log Flow Bottom Sheet
            if (showLogSheet) {
                LogBottomSheet(
                    draft = logDraft,
                    onDismiss = { viewModel.closeLogFlow() },
                    onUpdateFlow = { flow -> viewModel.updateFlow(flow) },
                    onToggleSymptom = { symptomId -> viewModel.toggleSymptom(symptomId) },
                    onUpdateSymptomSeverity = { symptomId, sev -> viewModel.updateSymptomSeverity(symptomId, sev) },
                    onToggleMood = { moodId -> viewModel.toggleMood(moodId) },
                    onUpdateEnergy = { energy -> viewModel.updateEnergyLevel(energy) },
                    onUpdateSleep = { sleep -> viewModel.updateSleepQuality(sleep) },
                    onUpdateNotes = { notes -> viewModel.updateNotes(notes) },
                    onSave = { onSuccess ->
                        viewModel.saveDraft {
                            onSuccess()
                        }
                    }
                )
            }

            // Day Detail Bottom Sheet
            if (showDayDetailSheet && dayDetailDate != null) {
                DayDetailBottomSheet(
                    date = dayDetailDate!!,
                    log = dayDetailLog,
                    onDismiss = { viewModel.closeDayDetail() },
                    onOpenLogFlow = { date -> viewModel.openLogFlow(date) }
                )
            }

            // Doctor-Visit Health Report Dialog
            if (showDoctorReportDialog) {
                DoctorReportExportDialog(
                    onDismissRequest = { showDoctorReportDialog = false },
                    onShareReport = { format, rangeMonths ->
                        viewModel.createShareableDoctorReport(format, rangeMonths) { uri, fileName ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = format.mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Aura Cycle - Doctor Health Report ($fileName)")
                                putExtra(Intent.EXTRA_TEXT, "Here is my menstrual cycle and symptom history report from Aura Cycle.")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Doctor Report"))
                        }
                    },
                    onSaveReport = { format, rangeMonths ->
                        pendingExportFormat = format
                        pendingExportRangeMonths = rangeMonths
                        val fileName = "aura_doctor_report_${LocalDate.now()}.${format.extension}"
                        saveReportLauncher.launch(fileName)
                    }
                )
            }
        }
    }
}
