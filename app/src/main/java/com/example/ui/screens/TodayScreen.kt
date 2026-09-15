package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DailyLogEntity
import com.example.domain.CyclePredictionState
import com.example.domain.PhaseExpectation
import com.example.ui.components.CycleRing
import com.example.ui.components.WhatToExpectCard
import com.example.ui.theme.FrauncesFontFamily
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    predictionState: CyclePredictionState,
    expectation: PhaseExpectation?,
    todayLog: DailyLogEntity?,
    onOpenLog: (LocalDate) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    userName: String? = null,
    showDailyGreeting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .testTag("today_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Time-based cycling personalized greeting (Fixed height top anchor)
        PersonalizedGreetingHeader(
            userName = userName,
            today = today,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Hero Cycle Card (ALWAYS in the exact same fixed position, even in learning phase)
        CycleRing(
            predictionState = predictionState,
            modifier = Modifier.testTag("cycle_ring_hero")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. "+ Log today" / "Logged today · Edit" Button
        val isLogged = todayLog != null
        OutlinedButton(
            onClick = { onOpenLog(today) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("log_today_button"),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isLogged) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isLogged) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLogged) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Logged today · Edit",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "+ Log today",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. What to Expect Card (Scrollable below without pushing hero card)
        if (expectation != null) {
            WhatToExpectCard(
                expectation = expectation,
                isToday = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PersonalizedGreetingHeader(
    userName: String?,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val displayName = if (!userName.isNullOrBlank()) userName.trim() else "Palkin"

    // Time of day determination
    val currentHour = remember { LocalTime.now().hour }
    val greetingsForTime = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> listOf("Good morning", "Rise and shine", "Peaceful morning", "Calm morning")
            in 12..16 -> listOf("Good afternoon", "Hello", "Welcome back", "Sunlit afternoon")
            in 17..21 -> listOf("Good evening", "Time to unwind", "Peaceful evening", "Golden evening")
            else -> listOf("Good night", "Rest easy", "Quiet night", "Sleep peacefully")
        }
    }

    var cycleIndex by remember { mutableIntStateOf(0) }

    // Cycle through greetings periodically (every 7 seconds)
    LaunchedEffect(greetingsForTime) {
        while (true) {
            delay(7000L)
            cycleIndex = (cycleIndex + 1) % greetingsForTime.size
        }
    }

    val activeGreeting = greetingsForTime[cycleIndex % greetingsForTime.size]
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()) }
    val formattedDate = remember(today) { today.format(dateFormatter) }

    Column(
        modifier = modifier
            .testTag("today_personalized_greeting")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                cycleIndex = (cycleIndex + 1) % greetingsForTime.size
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = activeGreeting,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "greeting_cycle_anim"
        ) { greetingText ->
            Text(
                text = "$greetingText, $displayName",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}
