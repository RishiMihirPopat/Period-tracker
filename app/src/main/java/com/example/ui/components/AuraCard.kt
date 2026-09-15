package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.LightBackground

/**
 * Background brush for the application canvas matching Figma tokens.
 */
@Composable
fun auraBackgroundBrush(): Brush {
    val isDark = isSystemInDarkTheme()
    return appBackgroundGradient(isDark)
}

fun appBackgroundGradient(isDark: Boolean): Brush {
    val bg = if (isDark) DarkBackground else LightBackground
    return Brush.verticalGradient(
        0.0f to bg,
        1.0f to bg
    )
}

/**
 * Standard minimal line icon treatment.
 */
@Composable
fun AuraIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
    badgeSize: Dp = 24.dp,
    iconSize: Dp = 20.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(badgeSize)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Editorial two-level section header:
 * Small uppercase tracked label paired with a refined display title.
 */
@Composable
fun AuraSectionHeader(
    eyebrow: String? = null,
    title: String,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    eyebrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    badgeTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!eyebrow.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (badgeIcon != null) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = eyebrowColor
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
        }
        Text(
            text = title,
            fontFamily = FrauncesFontFamily,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Light,
            color = titleColor
        )
    }
}

@Composable
fun CardTitle(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = title,
        fontFamily = FrauncesFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        color = color,
        modifier = modifier
    )
}

/**
 * Editorial Main Card container: 18dp radius per Figma spec, 1dp border.
 */
@Composable
fun AuraMainCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Nested sub-card container: 12dp radius, secondary background, 1dp border.
 */
@Composable
fun AuraSubCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Base AuraCard container: 12dp base radius.
 */
@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        elevation = elevation,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        elevation = elevation,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
