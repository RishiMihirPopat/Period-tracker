package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PastCycleInput
import com.example.ui.components.AuraMainCard
import com.example.ui.components.AuraSubCard
import com.example.ui.theme.FrauncesFontFamily
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PastCycleRowState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var date: LocalDate,
    var durationText: String = "5"
)

@Composable
fun PastCyclesScreen(
    onBack: () -> Unit,
    onSavePastCycles: (List<PastCycleInput>) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val rows = remember {
        mutableStateListOf(
            PastCycleRowState(date = today.minusDays(56), durationText = "5"),
            PastCycleRowState(date = today.minusDays(28), durationText = "5")
        )
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("past_cycles_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add past cycles",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Log your last few periods to get predictions sooner. We'll calculate cycle lengths automatically.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PastCyclesEditorList(
                rows = rows,
                onAddRow = {
                    val lastDate = rows.lastOrNull()?.date ?: today
                    rows.add(PastCycleRowState(date = lastDate.minusDays(28), durationText = "5"))
                },
                onDeleteRow = { index ->
                    if (rows.size > 1) {
                        rows.removeAt(index)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val inputs = rows.map { row ->
                        val duration = row.durationText.toIntOrNull() ?: 5
                        PastCycleInput(startDate = row.date, durationDays = duration)
                    }
                    onSavePastCycles(inputs)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_past_cycles_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Save past cycles",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PastCyclesEditorList(
    rows: MutableList<PastCycleRowState>,
    onAddRow: () -> Unit,
    onDeleteRow: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEachIndexed { index, rowState ->
            var rowDate by remember(rowState.id) { mutableStateOf(rowState.date) }
            var durationInput by remember(rowState.id) { mutableStateOf(rowState.durationText) }

            AuraMainCard(
                modifier = Modifier.fillMaxWidth().testTag("past_cycle_row_$index"),
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Period ${index + 1}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (rows.size > 1) {
                        IconButton(
                            onClick = { onDeleteRow(index) },
                            modifier = Modifier.size(28.dp).testTag("delete_past_cycle_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Picker row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    rowDate = newDate
                                    rowState.date = newDate
                                },
                                rowDate.year,
                                rowDate.monthValue - 1,
                                rowDate.dayOfMonth
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag("date_picker_button_$index")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select start date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Period start date",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rowDate.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Flow duration field
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { newValue ->
                        if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                            durationInput = newValue
                            rowState.durationText = newValue
                        }
                    },
                    label = { Text("Flow duration (optional)") },
                    placeholder = { Text("5") },
                    suffix = { Text("days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("duration_input_$index"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
        }

        OutlinedButton(
            onClick = onAddRow,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_another_cycle_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "+ Add another",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}
