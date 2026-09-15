package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.model.FlowIntensity
import com.example.domain.CyclePhase
import com.example.domain.CyclePredictionEngine
import com.example.domain.CyclePredictionState
import com.example.ui.components.AuraMainCard
import com.example.ui.theme.FrauncesFontFamily
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun CalendarScreen(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    monthLogs: List<DailyLogWithSymptoms>,
    predictionState: CyclePredictionState,
    cycles: List<CycleEntity> = emptyList(),
    onDayClick: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isDark = isSystemInDarkTheme()
    val logsByDate = monthLogs.associateBy { it.log.date }

    // Predicted fertile & period ranges
    val (predictedPeriodRange, fertileRange) = when (predictionState) {
        is CyclePredictionState.Predicted -> {
            val pStart = predictionState.predictedPeriodStart
            val pEnd = predictionState.predictedPeriodEnd
            val fStart = predictionState.fertileWindow.startDate
            val fEnd = predictionState.fertileWindow.endDate
            Pair(pStart..pEnd, fStart..fEnd)
        }
        else -> Pair(null, null)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("calendar_screen")
    ) {
        val minScreenHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = (minScreenHeight - 32.dp).coerceAtLeast(0.dp))
                    .padding(top = 28.dp, bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Clean Calendar Card (18dp radius)
                    AuraMainCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 20.dp
                    ) {
                        // Month Header with circular icon-button arrows either side of month/year label (Fraunces ~18sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrevMonth,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .testTag("prev_month_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                fontFamily = FrauncesFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .testTag("next_month_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 7-column day-of-week header row, small uppercase muted labels
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val daysOfWeek = listOf(
                                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
                            )
                            for (dow in daysOfWeek) {
                                Text(
                                    text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Month Days Grid
                        val firstDayOfMonth = currentMonth.atDay(1)
                        val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0
                        val daysInMonth = currentMonth.lengthOfMonth()
                        val totalCells = ((firstDayOfWeekIndex + daysInMonth + 6) / 7) * 7

                        val startDate = firstDayOfMonth.minusDays(firstDayOfWeekIndex.toLong())

                        Column(modifier = Modifier.fillMaxWidth()) {
                            var cellIndex = 0
                            while (cellIndex < totalCells) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (col in 0..6) {
                                        val cellDate = startDate.plusDays(cellIndex.toLong())
                                        val isCurrentMonth = cellDate.month == currentMonth.month
                                        val isSelected = cellDate == selectedDate
                                        val logWithSymptoms = logsByDate[cellDate]
                                        val log = logWithSymptoms?.log
                                        val isPeriod = log != null && log.flowIntensity != FlowIntensity.NONE
                                        val isPredictedPeriod = !isPeriod && isDatePredictedPeriod(cellDate, predictionState)
                                        val hasOtherData = logWithSymptoms != null && !isPeriod && (
                                            logWithSymptoms.symptoms.isNotEmpty() ||
                                            (log?.mood?.isNotEmpty() == true) ||
                                            (log?.notes?.isNotBlank() == true) ||
                                            log?.energyLevel != null ||
                                            log?.sleepQuality != null ||
                                            log?.sexualActivity != null ||
                                            log?.medicationTaken != null
                                        )
                                        val isFuture = cellDate.isAfter(LocalDate.now())
                                        val isTapEnabled = !isFuture || isPredictedPeriod
                                        val phaseLetter = getPhaseLetterForDate(cellDate, cycles, predictionState)

                                        CalendarDayCell(
                                            date = cellDate,
                                            isCurrentMonth = isCurrentMonth,
                                            isSelected = isSelected,
                                            isPeriod = isPeriod,
                                            isPredictedPeriod = isPredictedPeriod,
                                            hasOtherData = hasOtherData,
                                            isFuture = isFuture,
                                            isTapEnabled = isTapEnabled,
                                            phaseLetter = phaseLetter,
                                            onClick = { onDayClick(cellDate) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        cellIndex++
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Two-row Legend: Period / Predicted / Logged row + M / F / O / L Phase row
                        CalendarLegend()
                    }
                }
            }
        }
    }
}

/**
 * Computes a subtle single-letter phase indicator (M, F, O, L) for a given calendar date.
 */
private fun getPhaseLetterForDate(
    date: LocalDate,
    cycles: List<CycleEntity>,
    predictionState: CyclePredictionState
): String? {
    // 1. Check if date falls in a recorded cycle
    val cycle = cycles.firstOrNull { c ->
        !date.isBefore(c.startDate) && (c.endDate == null || !date.isAfter(c.endDate))
    }
    if (cycle != null) {
        val cycleLength = cycle.cycleLengthDays
            ?: if (predictionState is CyclePredictionState.Predicted) predictionState.predictedCycleLength
            else 28
        val periodDuration = cycle.periodLengthDays
        val phase = CyclePredictionEngine.determinePhaseForDate(
            date = date,
            cycleStartDate = cycle.startDate,
            cycleLength = cycleLength,
            periodDuration = periodDuration
        )
        return when (phase) {
            CyclePhase.MENSTRUAL -> "M"
            CyclePhase.FOLLICULAR -> "F"
            CyclePhase.OVULATORY -> "O"
            CyclePhase.LUTEAL -> "L"
        }
    }

    // 2. Check if predictionState has an active cycle start date
    val cycleStartDate = predictionState.currentCycleStartDate ?: return null
    val cycleLength = when (predictionState) {
        is CyclePredictionState.Predicted -> predictionState.predictedCycleLength
        is CyclePredictionState.AwaitingNextCycle -> predictionState.averageCycleLength
        is CyclePredictionState.LearningCycle -> 28
    }
    val phase = CyclePredictionEngine.determinePhaseForDate(
        date = date,
        cycleStartDate = cycleStartDate,
        cycleLength = cycleLength,
        periodDuration = 5
    )
    return when (phase) {
        CyclePhase.MENSTRUAL -> "M"
        CyclePhase.FOLLICULAR -> "F"
        CyclePhase.OVULATORY -> "O"
        CyclePhase.LUTEAL -> "L"
    }
}

/**
 * Checks if a future date falls into a predicted period window.
 */
private fun isDatePredictedPeriod(
    date: LocalDate,
    predictionState: CyclePredictionState,
    today: LocalDate = LocalDate.now()
): Boolean {
    if (date.isBefore(today)) return false
    val currentCycleStart = predictionState.currentCycleStartDate ?: return false
    val cycleLength = when (predictionState) {
        is CyclePredictionState.Predicted -> predictionState.predictedCycleLength
        is CyclePredictionState.AwaitingNextCycle -> predictionState.averageCycleLength
        is CyclePredictionState.LearningCycle -> 28
    }.coerceAtLeast(20)

    val periodLength = when (predictionState) {
        is CyclePredictionState.Predicted -> {
            (ChronoUnit.DAYS.between(
                predictionState.predictedPeriodStart,
                predictionState.predictedPeriodEnd
            ).toInt() + 1).coerceIn(1, 10)
        }
        else -> 5
    }

    if (predictionState is CyclePredictionState.Predicted) {
        if (!date.isBefore(predictionState.predictedPeriodStart) && !date.isAfter(predictionState.predictedPeriodEnd)) {
            return true
        }
    }

    if (date.isBefore(currentCycleStart)) return false

    val daysSinceStart = ChronoUnit.DAYS.between(currentCycleStart, date).toInt()
    val cycleIndex = daysSinceStart / cycleLength
    val dayInCycle = (daysSinceStart % cycleLength) + 1

    return cycleIndex >= 1 && dayInCycle in 1..periodLength
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isPeriod: Boolean,
    isPredictedPeriod: Boolean,
    hasOtherData: Boolean,
    isFuture: Boolean,
    isTapEnabled: Boolean,
    phaseLetter: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = date == LocalDate.now()
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .then(
                if (isTapEnabled) Modifier.clickable { onClick() }
                else Modifier
            )
            .testTag("day_${date.dayOfMonth}")
    ) {
        // Small single-letter phase tag (M, F, O, or L) in top-right corner, tiny (8sp), neutral muted-gray
        if (phaseLetter != null && isCurrentMonth) {
            Text(
                text = phaseLetter,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isFuture) 0.35f else 0.5f
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 3.dp)
            )
        }

        when {
            // Logged period days: solid filled circle, accent color, accent-foreground text
            isPeriod -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Predicted period days: dashed/outline ring, accent color, no fill
            isPredictedPeriod -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .drawBehind {
                            val strokeWidth = 1.5.dp.toPx()
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            drawCircle(
                                color = primaryColor,
                                radius = size.minDimension / 2 - strokeWidth,
                                style = Stroke(width = strokeWidth, pathEffect = dashEffect)
                            )
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = primaryColor
                        )
                        if (hasOtherData) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                            )
                        }
                    }
                }
            }

            // Today: neutral outlined circle (not accent-colored)
            isToday -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (hasOtherData) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                    }
                }
            }

            // Other logged or unlogged days: plain numeral on neutral grid, small accent dot if logged
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = when {
                            !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    if (hasOtherData) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two compact legend rows:
 * Row 1: Period (solid filled circle), Predicted (dashed ring), Logged (small dot)
 * Row 2: Phase tags (M: Menstrual · F: Follicular · O: Ovulatory · L: Luteal) - purely typographic, neutral
 */
@Composable
private fun CalendarLegend() {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_legend"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Period / Predicted / Logged
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Period (solid filled circle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Text(
                    text = "Period",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Predicted period (dashed ring)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .drawBehind {
                            val strokeWidth = 1.2.dp.toPx()
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                            drawCircle(
                                color = primaryColor,
                                radius = size.minDimension / 2 - strokeWidth,
                                style = Stroke(width = strokeWidth, pathEffect = dashEffect)
                            )
                        }
                )
                Text(
                    text = "Predicted",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Logged entry (dot)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Text(
                    text = "Logged",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Row 2: Phase Tags (M: Menstrual · F: Follicular · O: Ovulatory · L: Luteal) - purely typographic, neutral
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val phases = listOf(
                "M" to "Menstrual",
                "F" to "Follicular",
                "O" to "Ovulatory",
                "L" to "Luteal"
            )
            for ((letter, name) in phases) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
