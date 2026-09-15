package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.CyclePhase
import com.example.domain.CyclePredictionState
import com.example.domain.PeriodTimingStatus
import com.example.ui.theme.FrauncesFontFamily

@Composable
fun CycleRing(
    predictionState: CyclePredictionState,
    modifier: Modifier = Modifier
) {
    AuraMainCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 24.dp
    ) {
        when (predictionState) {
            is CyclePredictionState.Predicted -> {
                PredictedCycleRingContent(state = predictionState)
            }
            is CyclePredictionState.LearningCycle -> {
                LearningCycleRingContent(state = predictionState)
            }
            is CyclePredictionState.AwaitingNextCycle -> {
                AwaitingCycleRingContent(state = predictionState)
            }
        }
    }
}

@Composable
private fun PredictedCycleRingContent(state: CyclePredictionState.Predicted) {
    val isDark = isSystemInDarkTheme()
    val currentDay = state.currentCycleDay
    val totalDays = state.predictedCycleLength
    val progress = (currentDay.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900),
        label = "hero_ring_progress"
    )

    val currentPhase = state.currentPhase
    val accentColor = MaterialTheme.colorScheme.primary
    val startToneColor = accentColor.copy(alpha = if (isDark) 0.32f else 0.28f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val density = LocalDensity.current
    val strokeWidthDp = 14.dp
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }

    val phaseDescription = when (currentPhase) {
        CyclePhase.MENSTRUAL -> "Reset"
        CyclePhase.FOLLICULAR -> "Rise"
        CyclePhase.OVULATORY -> "Peak"
        CyclePhase.LUTEAL -> "Wind-down"
    }

    val timingText = when (val timing = state.timingStatus) {
        is PeriodTimingStatus.Upcoming -> "next period in ${timing.daysRemaining}d"
        is PeriodTimingStatus.ExpectedToday -> "period expected today"
        is PeriodTimingStatus.Late -> {
            if (timing.daysLate == 1L) "period 1d late"
            else "period ${timing.daysLate}d late"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular ring ~200dp diameter, 14dp stroke
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val canvasSize = size.minDimension
                val radius = (canvasSize - strokeWidthPx) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                val arcTopLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                val arcBounds = Size(radius * 2f, radius * 2f)

                // Background track (full 360° ring sharing exact stroke path)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcBounds,
                    style = stroke
                )

                // Active progress arc flush on identical stroke path and center
                val sweepAngle = (animatedProgress * 360f).coerceAtLeast(6f)
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(startToneColor, accentColor),
                        start = Offset(centerOffset.x, centerOffset.y - radius),
                        end = Offset(centerOffset.x + radius, centerOffset.y + radius)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcBounds,
                    style = stroke
                )
            }

            // Inside ring, centered: cycle-day numeral in Fraunces weight 300 (~45sp) with "/ 28" beneath
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$currentDay",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 46.sp,
                    lineHeight = 48.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "/ $totalDays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Below ring, centered: Phase name in neutral text
        Text(
            text = currentPhase.displayName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        // One-word phase description in muted text
        Text(
            text = phaseDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Next period in Nd in smaller muted text
        Text(
            text = timingText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(2.dp))

        val rangeFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH) }
        val endDayFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("d", java.util.Locale.ENGLISH) }
        val rangeStr = if (state.periodRangeStart.month == state.periodRangeEnd.month) {
            "${state.periodRangeStart.format(rangeFormatter)}–${state.periodRangeEnd.format(endDayFormatter)}"
        } else {
            "${state.periodRangeStart.format(rangeFormatter)}–${state.periodRangeEnd.format(rangeFormatter)}"
        }

        Text(
            text = "Expected: $rangeStr" + if (state.isRangeWidenedDueToMissingness) " · wider window" else "",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 4 thin segment ticks sized proportionally to each phase's day-count
        PhaseSegmentTicks(
            currentPhase = currentPhase,
            cycleLength = totalDays,
            lutealLength = state.posteriorLutealPhase.toFloat()
        )
    }
}

@Composable
private fun PhaseSegmentTicks(
    currentPhase: CyclePhase?,
    cycleLength: Int,
    lutealLength: Float = 14f
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val inactiveTickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    // Proportional phase days (Menstrual ~5, Follicular ~8, Ovulatory ~3, Luteal derived from Bayesian posterior)
    val menstrualDays = 5f
    val ovulatoryDays = 3f
    val lutealDays = lutealLength.coerceIn(10f, 18f)
    val follicularDays = (cycleLength - menstrualDays - ovulatoryDays - lutealDays).coerceAtLeast(4f)
    val total = menstrualDays + follicularDays + ovulatoryDays + lutealDays

    val phases = listOf(
        Triple(CyclePhase.MENSTRUAL, "M", menstrualDays / total),
        Triple(CyclePhase.FOLLICULAR, "F", follicularDays / total),
        Triple(CyclePhase.OVULATORY, "O", ovulatoryDays / total),
        Triple(CyclePhase.LUTEAL, "L", lutealDays / total)
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            phases.forEach { (phase, _, weight) ->
                val isActive = phase == currentPhase
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isActive) accentColor else inactiveTickColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            phases.forEach { (phase, letter, _) ->
                val isActive = phase == currentPhase
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LearningCycleRingContent(state: CyclePredictionState.LearningCycle) {
    val isDark = isSystemInDarkTheme()
    val completed = state.completedCyclesCount
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val primaryColor = MaterialTheme.colorScheme.primary

    val density = LocalDensity.current
    val strokeWidthDp = 14.dp
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cold start ring: same 200dp shape, empty track
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val canvasSize = size.minDimension
                val radius = (canvasSize - strokeWidthPx) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$completed/2",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "cycles logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Learning Phase",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = primaryColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Log 2 periods to start getting predictions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        PhaseSegmentTicks(
            currentPhase = null,
            cycleLength = 28
        )
    }
}

@Composable
private fun AwaitingCycleRingContent(state: CyclePredictionState.AwaitingNextCycle) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val primaryColor = MaterialTheme.colorScheme.primary

    val density = LocalDensity.current
    val strokeWidthDp = 14.dp
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val canvasSize = size.minDimension
                val radius = (canvasSize - strokeWidthPx) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Ready",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "for next cycle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Next Cycle",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = primaryColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Log when bleeding starts to begin tracking",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        PhaseSegmentTicks(
            currentPhase = null,
            cycleLength = 28
        )
    }
}
