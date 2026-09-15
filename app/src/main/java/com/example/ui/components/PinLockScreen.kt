package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EditorialBorder
import com.example.ui.theme.EditorialSerifFontFamily
import com.example.ui.theme.MutedRichBrown

@Composable
fun PinLockScreen(
    correctPin: String,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleDigit(digit: String) {
        if (enteredPin.length < 4) {
            val updated = enteredPin + digit
            enteredPin = updated
            errorMessage = null

            if (updated.length == 4) {
                if (updated == correctPin) {
                    onUnlocked()
                } else {
                    errorMessage = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("pin_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Lock Icon
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "App Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PRIVATE LOGS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enter Passcode",
                fontFamily = EditorialSerifFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your 4-digit PIN to access your cycle history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN Dots Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MutedRichBrown
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isFilled) MutedRichBrown else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error Text
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } ?: Spacer(modifier = Modifier.height(32.dp))

            // Keypad Grid
            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in buttons) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (item in row) {
                            if (item.isEmpty()) {
                                Spacer(modifier = Modifier.size(68.dp))
                            } else if (item == "DEL") {
                                IconButton(
                                    onClick = { handleBackspace() },
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { handleDigit(item) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.dp, EditorialBorder),
                                    shape = CircleShape,
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = EditorialSerifFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
