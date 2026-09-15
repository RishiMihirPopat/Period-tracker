package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// NEUTRAL BASE (Color-Agnostic)
// Must look right under all 4 accents — no warm/brown tint here
// ============================================================================

// Dark mode: background #121214, card #1C1C1F, foreground text #EDEDEF, muted text #8E8E93, border #2A2A2E
val DarkBackground = Color(0xFF121214)
val DarkCard = Color(0xFF1C1C1F)
val DarkForeground = Color(0xFFEDEDEF)
val DarkMutedText = Color(0xFF8E8E93)
val DarkBorder = Color(0xFF2A2A2E)
val DarkSecondary = Color(0xFF242428)          // Nested sub-card neutral background
val DarkSecondaryText = Color(0xFF8E8E93)

// Light mode: background #FAFAFA, card #FFFFFF, foreground text #1C1C1F, muted text #6E6E73, border #E4E4E7
val LightBackground = Color(0xFFFAFAFA)
val LightCard = Color(0xFFFFFFFF)
val LightForeground = Color(0xFF1C1C1F)
val LightMutedText = Color(0xFF6E6E73)
val LightBorder = Color(0xFFE4E4E7)
val LightSecondary = Color(0xFFF4F4F5)         // Nested sub-card neutral background
val LightSecondaryText = Color(0xFF6E6E73)

// Backward-compatibility references for neutral tokens
val DarkCardText = DarkForeground
val LightCardText = LightForeground
val DarkMuted = DarkSecondary
val LightMuted = LightSecondary
val EditorialBorder = LightBorder
val EditorialDivider = LightSecondary
val EditorialBorderDark = DarkBorder

val NeutralBackgroundDark = DarkBackground
val NeutralSurfaceDark = DarkCard
val NeutralSurfaceVariantDark = DarkSecondary
val NeutralTextDark = DarkForeground
val NeutralTextMutedDark = DarkMutedText
val NeutralBorderDark = DarkBorder

val NeutralBackgroundLight = LightBackground
val NeutralSurfaceLight = LightCard
val NeutralSurfaceSubtle = LightSecondary
val NeutralSurfaceVariant = LightMuted
val NeutralHeadline = LightForeground
val NeutralBody = LightForeground
val NeutralMuted = LightMutedText
val NeutralCaption = LightMutedText

// ============================================================================
// ACCENT COLOR OPTIONS
// 4 options with dark-mode and light-mode variants:
//   Pink:        dark #EC7FA0   light #C94B72
//   Brown:       dark #C88A5D   light #A9633C
//   Royal Blue:  dark #5C7CFA   light #2F4B9E
//   Lavender:    dark #A78BFA   light #6B4FA0
// ============================================================================

enum class AppAccent(
    val id: String,
    val displayName: String,
    val darkColor: Color,
    val lightColor: Color
) {
    PINK(
        id = "Pink",
        displayName = "Pink",
        darkColor = Color(0xFFEC7FA0),
        lightColor = Color(0xFFC94B72)
    ),
    BROWN(
        id = "Brown",
        displayName = "Brown",
        darkColor = Color(0xFFC88A5D),
        lightColor = Color(0xFFA9633C)
    ),
    ROYAL_BLUE(
        id = "Royal Blue",
        displayName = "Royal Blue",
        darkColor = Color(0xFF5C7CFA),
        lightColor = Color(0xFF2F4B9E)
    ),
    LAVENDER(
        id = "Lavender",
        displayName = "Lavender",
        darkColor = Color(0xFFA78BFA),
        lightColor = Color(0xFF6B4FA0)
    );

    fun color(isDark: Boolean): Color = if (isDark) darkColor else lightColor

    companion object {
        fun fromId(id: String?): AppAccent =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: BROWN
    }
}

// Default accent colors (Brown)
val DarkPrimary = AppAccent.BROWN.darkColor
val DarkPrimaryForeground = Color(0xFFFFFFFF)
val LightPrimary = AppAccent.BROWN.lightColor
val LightPrimaryForeground = Color(0xFFFFFFFF)

val DarkAccent = DarkPrimary
val DarkAccentForeground = Color(0xFFFFFFFF)
val LightAccent = LightPrimary
val LightAccentForeground = Color(0xFFFFFFFF)
val DarkRingBaseTone = Color(0xFF5A3D2A)
val LightRingBaseTone = Color(0xFFD9B7A0)

// Flow intensity neutral tones
val FlowNone = Color(0xFF8E8E93)
val FlowSpotting = Color(0xFF8E8E93)
val FlowLight = Color(0xFF8E8E93)
val FlowMedium = Color(0xFF8E8E93)
val FlowHeavy = Color(0xFF8E8E93)

// Compatibility aliases
val MutedRichBrown = DarkPrimary
val MutedRichBrownLight = DarkAccent
val MutedRichBrownDark = DarkRingBaseTone
val MutedRichBrownContainer = DarkSecondary
val OnMutedRichBrownContainer = DarkSecondaryText
val WarmRosewood = DarkAccent
val WarmRosewoodContainer = DarkSecondary
val OnWarmRosewoodContainer = DarkSecondaryText

/**
 * Retained for legacy signature compatibility. In the new single-accent design system,
 * components use MaterialTheme.colorScheme.primary and neutral tokens directly.
 */
fun getPhaseColor(phase: com.example.domain.CyclePhase, isDark: Boolean = true): Color {
    return if (isDark) DarkMutedText else LightMutedText
}
