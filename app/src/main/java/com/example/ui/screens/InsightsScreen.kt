package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TagLookup
import com.example.domain.CyclePredictionState
import com.example.domain.CycleStats
import com.example.domain.PhaseExpectation
import com.example.ui.components.AuraMainCard
import com.example.ui.theme.FrauncesFontFamily
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(
    stats: CycleStats,
    predictionState: CyclePredictionState,
    expectation: PhaseExpectation?,
    onOpenDoctorReportDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("insights_screen")
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
                    // 1. Phase Tip Card (shown when phase is known)
                    if (expectation != null) {
                        AuraMainCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 20.dp
                        ) {
                            Text(
                                text = "CURRENT PHASE · ${expectation.phase.displayName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.4.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = expectation.plainGuidanceSummary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // 2. Vertical stack of insight rows inside an AuraMainCard
                    AuraMainCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 20.dp
                    ) {
                        Text(
                            text = "Key Patterns",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 1: Next period
                        val (nextPeriodValue, nextPeriodSub) = when (predictionState) {
                            is CyclePredictionState.Predicted -> {
                                val pStart = predictionState.periodRangeStart.format(DateTimeFormatter.ofPattern("MMM d"))
                                val pEnd = if (predictionState.periodRangeStart.month == predictionState.periodRangeEnd.month) {
                                    predictionState.periodRangeEnd.format(DateTimeFormatter.ofPattern("d"))
                                } else {
                                    predictionState.periodRangeEnd.format(DateTimeFormatter.ofPattern("MMM d"))
                                }
                                val subText = when {
                                    predictionState.isRangeWidenedDueToMissingness && predictionState.outlierCyclesCount > 0 ->
                                        "Window widened for unlogged days, smoothed to balance past cycle changes."
                                    predictionState.isRangeWidenedDueToMissingness ->
                                        "Window widened slightly to give you room for unlogged days."
                                    predictionState.outlierCyclesCount > 0 ->
                                        "Estimated from your typical cycle rhythm, ignoring unusual delays."
                                    else ->
                                        "Estimated from your typical ${predictionState.predictedCycleLength}-day cycle."
                                }
                                Pair("$pStart – $pEnd", subText)
                            }
                            is CyclePredictionState.LearningCycle -> Pair("Learning phase", "Log 2 full cycles so Aura can learn your personal rhythm.")
                            is CyclePredictionState.AwaitingNextCycle -> Pair("Awaiting next period", "Log the start of your period when bleeding begins.")
                        }
                        InsightItemRow(
                            icon = Icons.Outlined.WaterDrop,
                            iconTint = MaterialTheme.colorScheme.primary,
                            eyebrow = "NEXT PERIOD",
                            value = nextPeriodValue,
                            supporting = nextPeriodSub
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 2: Fertile window
                        val (fertileValue, fertileSub) = when (predictionState) {
                            is CyclePredictionState.Predicted -> {
                                val fStart = predictionState.fertileWindow.startDate.format(DateTimeFormatter.ofPattern("MMM d"))
                                val fEnd = predictionState.fertileWindow.endDate.format(DateTimeFormatter.ofPattern("d"))
                                val subText = if (predictionState.hasSymptomLutealAdjustment && predictionState.symptomAdjustmentName != null) {
                                    val friendlySymptom = TagLookup.getSymptomById(predictionState.symptomAdjustmentName)?.name?.lowercase()
                                        ?: predictionState.symptomAdjustmentName.replace('_', ' ').lowercase()
                                    "Adjusted based on your recurring $friendlySymptom pattern."
                                } else {
                                    "Estimated highest chance of conception."
                                }
                                Pair("$fStart – $fEnd", subText)
                            }
                            else -> Pair("Available soon", "Requires 2 completed cycles to estimate")
                        }
                        InsightItemRow(
                            icon = Icons.Outlined.Favorite,
                            iconTint = MaterialTheme.colorScheme.primary,
                            eyebrow = "FERTILE WINDOW",
                            value = fertileValue,
                            supporting = fertileSub
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 3: Most common symptom
                        val topSymptom = stats.symptomFrequencies.firstOrNull()
                        val symptomName = topSymptom?.let { TagLookup.getSymptomById(it.symptomId)?.name ?: it.symptomId }
                        val (symptomValue, symptomSub) = if (topSymptom != null && symptomName != null) {
                            Pair(symptomName, "Logged in ${topSymptom.percentage}% of cycle logs (${topSymptom.count} times)")
                        } else {
                            Pair("None recorded", "Log daily symptoms to track patterns")
                        }
                        InsightItemRow(
                            icon = Icons.Outlined.Healing,
                            iconTint = MaterialTheme.colorScheme.primary,
                            eyebrow = "MOST COMMON SYMPTOM",
                            value = symptomValue,
                            supporting = symptomSub
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 4: Best phase for energy
                        InsightItemRow(
                            icon = Icons.Outlined.Bolt,
                            iconTint = MaterialTheme.colorScheme.primary,
                            eyebrow = "PEAK ENERGY PHASE",
                            value = "Ovulatory Phase",
                            supporting = "Optimal stamina, social confidence, and focus"
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Cycle Metrics Summary (2 cards in a row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AuraMainCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = 16.dp
                        ) {
                            Text(
                                text = "AVG CYCLE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.1.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stats.averageCycleLengthDays?.let { "$it days" } ?: "--",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Based on ${stats.totalCyclesTracked} cycles",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AuraMainCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = 16.dp
                        ) {
                            Text(
                                text = "AVG PERIOD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.1.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stats.averagePeriodLengthDays?.let { "$it days" } ?: "--",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Bleeding duration",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Doctor-Visit Health Report Card
                    AuraMainCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("insights_doctor_report_card"),
                        contentPadding = 18.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.HealthAndSafety,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DOCTOR-VISIT REPORT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Share with your doctor",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Export your LMP, cycle history, and symptom timing (e.g. migraines, cramps) as a clean PDF or CSV.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onOpenDoctorReportDialog,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("open_doctor_report_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Doctor's Report (PDF / CSV)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightItemRow(
    icon: ImageVector,
    iconTint: Color,
    eyebrow: String,
    value: String,
    supporting: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tinted rounded-square badge on left
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Eyebrow label + bold value + muted supporting line
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
