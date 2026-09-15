package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CycleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_LOG_TODAY = "com.example.ACTION_LOG_TODAY"
        const val EXTRA_OPEN_LOG = "extra_open_log"
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            providerScope.launch {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch
                val componentName = ComponentName(context, CycleWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds.isNotEmpty()) {
                    val resolver = CycleWidgetDataResolver(context)
                    val data = resolver.resolveWidgetData()
                    for (id in widgetIds) {
                        renderWidget(context, appWidgetManager, id, data)
                    }
                }
            }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: CycleWidgetData
        ) {
            val remoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val compact = buildRemoteViews(context, data, isExpanded = false)
                val expanded = buildRemoteViews(context, data, isExpanded = true)
                RemoteViews(
                    mapOf(
                        SizeF(60f, 60f) to compact,
                        SizeF(200f, 60f) to expanded
                    )
                )
            } else {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val isExpanded = minWidth >= 220
                buildRemoteViews(context, data, isExpanded)
            }
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        private fun buildRemoteViews(
            context: Context,
            data: CycleWidgetData,
            isExpanded: Boolean
        ): RemoteViews {
            val layoutId = if (isExpanded) {
                R.layout.widget_cycle_expanded
            } else {
                R.layout.widget_cycle_compact
            }

            val views = RemoteViews(context.packageName, layoutId)

            // Header & Hero
            views.setTextViewText(R.id.widget_cycle_day, data.cycleDayText)
            views.setTextViewText(R.id.widget_phase_pill, data.phaseName)
            views.setTextViewText(R.id.widget_milestone_text, data.milestoneText)

            // Dynamic Phase Styling (Samsung One UI 8 Pill and Status Dot)
            views.setImageViewResource(R.id.widget_phase_pill_bg, data.phasePillDrawable)
            views.setTextColor(R.id.widget_phase_pill, context.getColor(data.phaseTextColor))
            views.setImageViewResource(R.id.widget_dot, data.phaseDotDrawable)

            // Expanded-specific elements
            if (isExpanded) {
                views.setTextViewText(R.id.widget_progress_label, data.progressLabelText)
                views.setProgressBar(R.id.widget_progress_bar, 100, data.progressPercent, false)
                views.setTextViewText(R.id.widget_log_status_text, data.todayLogText)
            }

            // Body click -> Opens MainActivity
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            // Quick Log button click -> Opens MainActivity with ACTION_LOG_TODAY
            val logIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_LOG_TODAY
                putExtra(EXTRA_OPEN_LOG, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val logPendingIntent = PendingIntent.getActivity(
                context,
                101,
                logIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_quick_log, logPendingIntent)

            return views
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Immediately render default data synchronously to eliminate loading flash or failure
        for (id in appWidgetIds) {
            renderWidget(context, appWidgetManager, id, CycleWidgetDataResolver.DEFAULT_DATA)
        }
        providerScope.launch {
            val resolver = CycleWidgetDataResolver(context)
            val data = resolver.resolveWidgetData()
            for (id in appWidgetIds) {
                renderWidget(context, appWidgetManager, id, data)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        providerScope.launch {
            val resolver = CycleWidgetDataResolver(context)
            val data = resolver.resolveWidgetData()
            renderWidget(context, appWidgetManager, appWidgetId, data)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REFRESH_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }
}
