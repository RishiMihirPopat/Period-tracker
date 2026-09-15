package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.PastCycleInput
import com.example.ui.components.AuraMainCard
import com.example.ui.components.AuraSubCard
import com.example.ui.theme.FrauncesFontFamily
import java.time.LocalDate

enum class OnboardingStep {
    WELCOME,
    NAME,
    PAST_CYCLES,
    DONE
}

@Composable
fun OnboardingScreen(
    onComplete: (name: String, pastCycles: List<PastCycleInput>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    val today = LocalDate.now()

    val cycleRows = remember {
        mutableStateListOf(
            PastCycleRowState(date = today.minusDays(56), durationText = "5"),
            PastCycleRowState(date = today.minusDays(28), durationText = "5")
        )
    }
    var savedPastCycles by remember { mutableStateOf<List<PastCycleInput>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onComplete(userName.trim(), savedPastCycles)
    }

    val scrollState = rememberScrollState()

    // Reset scroll position to top when switching between onboarding steps
    LaunchedEffect(stepIndex) {
        scrollState.scrollTo(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step progress indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { i ->
                    val isActive = i == stepIndex
                    val isPassed = i < stepIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (isActive) 24.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step_transition",
                modifier = Modifier.fillMaxWidth()
            ) { currentStep ->
                when (currentStep) {
                    0 -> {
                        // a. Welcome Step
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "cycle",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 38.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("onboarding_welcome_title")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Quiet, private cycle tracking designed around your body's natural rhythms.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(36.dp))

                            AuraMainCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = 20.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Everything stays on this phone.",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "No accounts, no analytics, and no cloud servers. Your personal health data never leaves your device.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(48.dp))

                            Button(
                                onClick = { stepIndex = 1 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_get_started_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Get started",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    1 -> {
                        // b. Name Step
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "What should we call you?",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("onboarding_name_title")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "A first name or nickname for your daily greeting.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                placeholder = { Text("Your name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_name_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(36.dp))

                            Button(
                                onClick = { stepIndex = 2 },
                                enabled = userName.trim().isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_continue_name_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Continue",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    2 -> {
                        // c. Optional Past Cycles Step
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Want predictions right away? Add your last couple of periods.",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("onboarding_past_cycles_title")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Add 2 or more remembered period start dates to calculate your baseline cycles immediately.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            PastCyclesEditorList(
                                rows = cycleRows,
                                onAddRow = {
                                    val lastDate = cycleRows.lastOrNull()?.date ?: today
                                    cycleRows.add(PastCycleRowState(date = lastDate.minusDays(28), durationText = "5"))
                                },
                                onDeleteRow = { index ->
                                    if (cycleRows.size > 1) {
                                        cycleRows.removeAt(index)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    savedPastCycles = cycleRows.map { row ->
                                        val duration = row.durationText.toIntOrNull() ?: 5
                                        PastCycleInput(startDate = row.date, durationDays = duration)
                                    }
                                    stepIndex = 3
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("onboarding_add_cycles_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = {
                                    savedPastCycles = emptyList()
                                    stepIndex = 3
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("onboarding_skip_button")
                            ) {
                                Text(
                                    text = "Skip for now",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }

                    3 -> {
                        // d. Done Step
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(36.dp))

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "You're all set, ${userName.trim()}.",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("onboarding_done_title")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = if (savedPastCycles.isNotEmpty()) {
                                    "Your baseline cycles have been loaded. Predictions and patterns will update as you log."
                                } else {
                                    "Your cycle space is ready. Start logging anytime to build your predictions."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(48.dp))

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            onComplete(userName.trim(), savedPastCycles)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        onComplete(userName.trim(), savedPastCycles)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_finish_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Go to Today",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }
        }
    }
}
