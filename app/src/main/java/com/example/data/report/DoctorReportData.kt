package com.example.data.report

import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import java.time.LocalDate

enum class ReportFormat(val extension: String, val mimeType: String, val displayName: String) {
    PDF("pdf", "application/pdf", "PDF Document"),
    CSV("csv", "text/csv", "CSV Spreadsheet")
}

data class DoctorCycleRow(
    val cycleNumber: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val cycleLengthDays: Int?,
    val periodLengthDays: Int,
    val isOngoing: Boolean,
    val regularityNote: String
)

data class DoctorSymptomSummary(
    val symptomTag: String,
    val displayName: String,
    val totalEpisodes: Int,
    val primaryPhaseOrTiming: String,
    val averageSeverity: Double, // 1.0 to 3.0
    val maxSeverity: Int,
    val cyclesAffectedPercentage: Int
)

data class DoctorDailyLogRow(
    val date: LocalDate,
    val cycleDay: Int?,
    val flowIntensity: FlowIntensity,
    val symptoms: List<Pair<String, Int>>, // name to severity
    val moods: List<String>,
    val energyLevel: EnergyLevel?,
    val sleepQuality: SleepQuality?,
    val bbtCelsius: Double?,
    val sexualActivity: SexualActivity?,
    val medicationTaken: Boolean?,
    val notes: String
)

data class DoctorReportData(
    val patientName: String,
    val generatedDate: LocalDate,
    val dateRangeDescription: String,
    val lastPeriodDate: LocalDate?,
    val currentCycleDay: Int?,
    val averageCycleLength: Double?,
    val cycleLengthVariationDays: Double?,
    val cycleRegularityText: String,
    val averagePeriodLength: Double?,
    val totalRecordedCycles: Int,
    val cycles: List<DoctorCycleRow>,
    val symptomSummaries: List<DoctorSymptomSummary>,
    val dailyLogs: List<DoctorDailyLogRow>
)
