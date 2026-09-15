package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CycleDatabase
import com.example.data.repository.CycleRepository
import com.example.domain.CyclePredictionEngine
import com.example.ui.MainAppNavigation
import com.example.ui.MainViewModel
import com.example.ui.theme.CycleTheme
import com.example.widget.CycleWidgetProvider
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = CycleDatabase.getInstance(applicationContext)
        val backupManager = com.example.data.backup.CycleBackupManager(
            database = database,
            cycleDao = database.cycleDao(),
            dailyLogDao = database.dailyLogDao(),
            symptomLogDao = database.symptomLogDao(),
            userSettingsDao = database.userSettingsDao(),
            profileDao = database.profileDao()
        )
        val repository = CycleRepository(
            dailyLogDao = database.dailyLogDao(),
            symptomLogDao = database.symptomLogDao(),
            cycleDao = database.cycleDao(),
            userSettingsDao = database.userSettingsDao(),
            profileDao = database.profileDao(),
            predictionEngine = CyclePredictionEngine(),
            backupManager = backupManager,
            context = applicationContext
        )

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, applicationContext) as T
            }
        }
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        handleWidgetIntent(intent)

        setContent {
            val settings by viewModel.userSettings.collectAsStateWithLifecycle()
            CycleTheme(accentColor = settings.accentColor) {
                MainAppNavigation(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) {
            handleWidgetIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        CycleWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == CycleWidgetProvider.ACTION_LOG_TODAY ||
            intent?.getBooleanExtra(CycleWidgetProvider.EXTRA_OPEN_LOG, false) == true
        ) {
            viewModel.openLogFlow(LocalDate.now())
        }
    }
}
