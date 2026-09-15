package com.example.data.model

enum class FlowIntensity {
    NONE,
    SPOTTING,
    LIGHT,
    MEDIUM,
    HEAVY
}

enum class SexualActivity {
    NONE,
    PROTECTED,
    UNPROTECTED
}

enum class EnergyLevel(val displayName: String) {
    LOW("Low"),
    BALANCED("Moderate"),
    HIGH("High"),
    PEAK("Peak")
}

enum class SleepQuality(val displayName: String) {
    POOR("Poor"),
    FAIR("Fair"),
    GOOD("Good"),
    DEEP("Deep")
}

data class SymptomTag(
    val id: String,
    val name: String,
    val category: String = "General"
) {
    val displayName: String get() = name
}

data class MoodTag(
    val id: String,
    val name: String,
    val emoji: String = ""
) {
    val displayName: String get() = name
}

object TagLookup {
    val commonSymptoms = listOf(
        SymptomTag("cramps", "Cramps", "Pain"),
        SymptomTag("headache", "Headache", "Pain"),
        SymptomTag("fatigue", "Fatigue", "Energy"),
        SymptomTag("bloating", "Bloating", "Digestion"),
        SymptomTag("acne", "Acne", "Skin"),
        SymptomTag("tender_breasts", "Tender breasts", "Body"),
        SymptomTag("backache", "Backache", "Pain"),
        SymptomTag("nausea", "Nausea", "Digestion"),
        SymptomTag("cravings", "Cravings", "Appetite"),
        SymptomTag("insomnia", "Insomnia", "Sleep")
    )

    val PRESEEDED_SYMPTOMS = commonSymptoms

    val commonMoods = listOf(
        MoodTag("calm", "Calm"),
        MoodTag("happy", "Happy"),
        MoodTag("energetic", "Energetic"),
        MoodTag("tired", "Tired"),
        MoodTag("stressed", "Stressed"),
        MoodTag("anxious", "Anxious"),
        MoodTag("irritable", "Irritable"),
        MoodTag("low", "Low")
    )

    val PRESEEDED_MOODS = commonMoods

    fun getSymptomById(id: String): SymptomTag? {
        return commonSymptoms.firstOrNull { it.id == id }
    }

    fun getMoodById(id: String): MoodTag? {
        return commonMoods.firstOrNull { it.id == id }
    }
}

data class PastCycleInput(
    val startDate: java.time.LocalDate,
    val durationDays: Int = 5
)
