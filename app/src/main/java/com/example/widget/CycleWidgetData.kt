package com.example.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class CycleWidgetData(
    val cycleDayText: String,
    val phaseName: String,
    @param:DrawableRes val phasePillDrawable: Int,
    @param:ColorRes val phaseTextColor: Int,
    @param:DrawableRes val phaseDotDrawable: Int,
    val milestoneText: String,
    val progressPercent: Int,
    val progressLabelText: String,
    val todayLogText: String,
    val isTracking: Boolean
)
