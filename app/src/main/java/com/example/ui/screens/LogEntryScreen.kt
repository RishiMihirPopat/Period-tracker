package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FlowIntensity
import com.example.data.model.SexualActivity
import com.example.data.model.TagLookup
import com.example.ui.LogDraft
import com.example.ui.components.AuraCard
import com.example.ui.components.AuraSectionHeader
import com.example.ui.theme.EditorialSerifFontFamily
import com.example.ui.theme.MutedRichBrown
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogEntryScreen(
    draft: LogDraft,
    onFlowChange: (FlowIntensity) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onUpdateSymptomSeverity: (String, Int) -> Unit = { _, _ -> },
    onToggleMood: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSexualActivityChange: (SexualActivity?) -> Unit,
    onMedicationChange: (Boolean?) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("log_entry_screen")
    ) {
        // Date Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier.size(36.dp).testTag("log_prev_day")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous Day",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = draft.date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = draft.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    fontFamily = EditorialSerifFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onNextDay,
                modifier = Modifier.size(36.dp).testTag("log_next_day")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next Day",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Flow Intensity Card
        AuraCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            AuraSectionHeader(
                eyebrow = "PERIOD FLOW",
                title = "Flow Intensity",
                badgeIcon = Icons.Outlined.WaterDrop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val flows = listOf(
                    FlowIntensity.NONE to "None",
                    FlowIntensity.SPOTTING to "Spot",
                    FlowIntensity.LIGHT to "Light",
                    FlowIntensity.MEDIUM to "Medium",
                    FlowIntensity.HEAVY to "Heavy"
                )
                for ((flow, label) in flows) {
                    val isSelected = draft.flowIntensity == flow
                    FlowCard(
                        label = label,
                        isSelected = isSelected,
                        onClick = { onFlowChange(flow) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("flow_${flow.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Symptoms Card
        AuraCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            AuraSectionHeader(
                eyebrow = "BODY & PHYSICAL",
                title = "Physical Symptoms",
                badgeIcon = Icons.Outlined.Healing
            )

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (symptom in TagLookup.PRESEEDED_SYMPTOMS) {
                    val severity = draft.symptoms[symptom.id]
                    val isSelected = severity != null
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleSymptom(symptom.id) },
                        label = {
                            val severitySuffix = if (isSelected) {
                                when (severity) {
                                    1 -> " · V.Mild"
                                    2 -> " · Mild"
                                    3 -> " · Mod"
                                    4 -> " · Severe"
                                    5 -> " · V.Sev"
                                    else -> " · Mod"
                                }
                            } else ""
                            Text(
                                text = "${symptom.displayName}$severitySuffix",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MutedRichBrown,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            selectedBorderColor = MutedRichBrown,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("symptom_${symptom.id}")
                    )
                }
            }

            if (draft.symptoms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((symId, sev) in draft.symptoms) {
                        val symName = TagLookup.getSymptomById(symId)?.name ?: symId
                        val currentLabel = when (sev) {
                            1 -> "1: Very Mild"
                            2 -> "2: Mild"
                            3 -> "3: Moderate"
                            4 -> "4: Severe"
                            5 -> "5: Very Severe"
                            else -> "$sev: Moderate"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = symName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MutedRichBrown
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val stops = listOf(
                                    1 to "1 (V.Mild)",
                                    2 to "2 (Mild)",
                                    3 to "3 (Mod)",
                                    4 to "4 (Sev)",
                                    5 to "5 (V.Sev)"
                                )
                                stops.forEach { (lvl, lbl) ->
                                    val isCur = sev == lvl
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCur) MutedRichBrown else Color.Transparent)
                                            .clickable { onUpdateSymptomSeverity(symId, lvl) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lbl,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCur) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Mood Card
        AuraCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            AuraSectionHeader(
                eyebrow = "MIND & EMOTIONS",
                title = "Emotional State",
                badgeIcon = Icons.Outlined.Mood
            )

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (mood in TagLookup.PRESEEDED_MOODS) {
                    val isSelected = draft.mood.contains(mood.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleMood(mood.id) },
                        label = {
                            Text(
                                text = "${mood.emoji} ${mood.displayName}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MutedRichBrown,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            selectedBorderColor = MutedRichBrown,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("mood_${mood.id}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Notes Card
        AuraCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            AuraSectionHeader(
                eyebrow = "REFLECTIONS",
                title = "Daily Journal",
                badgeIcon = Icons.Outlined.EditNote
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                placeholder = {
                    Text(
                        text = "Add personal thoughts or observations...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_input"),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Action Buttons (Save & Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_log_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Entry",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("clear_log_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Clear Entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FlowCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MutedRichBrown else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MutedRichBrown else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
