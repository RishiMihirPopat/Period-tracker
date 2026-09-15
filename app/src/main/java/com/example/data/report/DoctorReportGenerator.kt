package com.example.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.dao.CycleDao
import com.example.data.local.dao.DailyLogDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SymptomLogDao
import com.example.data.local.dao.UserSettingsDao
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import com.example.data.model.FlowIntensity
import com.example.data.model.TagLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DoctorReportGenerator(
    private val cycleDao: CycleDao,
    private val dailyLogDao: DailyLogDao,
    private val symptomLogDao: SymptomLogDao,
    private val profileDao: ProfileDao,
    private val userSettingsDao: UserSettingsDao
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

    suspend fun buildReportData(rangeMonths: Int? = 6): DoctorReportData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val startDateLimit = rangeMonths?.let { today.minusMonths(it.toLong()) }

        val profile = profileDao.getProfileSync()
        val patientName = profile?.name?.takeIf { it.isNotBlank() } ?: "Anonymous"

        val allCycles = cycleDao.getAllCyclesSync()
        val allLogs = dailyLogDao.getAllLogsWithSymptomsSync()

        val dateRangeDescription = if (rangeMonths != null) {
            "Past $rangeMonths Months (${startDateLimit!!.format(dateFormatter)} – ${today.format(dateFormatter)})"
        } else {
            "All Time (Up to ${today.format(dateFormatter)})"
        }

        // Filter cycles overlapping the range
        val relevantCycles = if (startDateLimit == null) {
            allCycles
        } else {
            allCycles.filter { cycle ->
                val end = cycle.endDate ?: today
                !end.isBefore(startDateLimit)
            }
        }

        // Most recent cycle / Last Menstrual Period (LMP)
        val latestCycle = allCycles.firstOrNull()
        val lastPeriodDate = latestCycle?.startDate ?: allLogs.firstOrNull { it.log.flowIntensity != FlowIntensity.NONE }?.log?.date
        val currentCycleDay = if (lastPeriodDate != null) {
            val days = ChronoUnit.DAYS.between(lastPeriodDate, today) + 1
            if (days in 1..90) days.toInt() else null
        } else null

        // Completed cycles for stats (cycle length between 18 and 60 days)
        val completedCycles = relevantCycles.filter {
            it.endDate != null && it.cycleLengthDays != null && it.cycleLengthDays in 18..60
        }

        val avgCycleLength = if (completedCycles.isNotEmpty()) {
            completedCycles.mapNotNull { it.cycleLengthDays }.average()
        } else null

        val cycleLengthSd = if (completedCycles.size >= 2 && avgCycleLength != null) {
            val variance = completedCycles.mapNotNull { it.cycleLengthDays }
                .sumOf { (it - avgCycleLength) * (it - avgCycleLength) } / (completedCycles.size - 1)
            sqrt(variance)
        } else null

        val cycleRegularityText = when {
            completedCycles.size < 2 -> "Baseline (< 2 cycles)"
            cycleLengthSd != null && cycleLengthSd <= 3.5 -> "Regular (Variation < 4 days)"
            cycleLengthSd != null && cycleLengthSd <= 7.0 -> "Mildly Variable"
            cycleLengthSd != null -> "Irregular (Variation > 7 days)"
            else -> "Regular"
        }

        val avgPeriodLength = if (relevantCycles.isNotEmpty()) {
            relevantCycles.map { it.periodLengthDays }.filter { it in 1..14 }.average().takeIf { !it.isNaN() }
        } else null

        // Format cycle rows
        val cycleRows = relevantCycles.mapIndexed { index, cycle ->
            val isOngoing = cycle.endDate == null
            val cycleNum = relevantCycles.size - index
            val regularityNote = when {
                isOngoing -> "Current / Ongoing"
                cycle.cycleLengthDays == null -> "Completed"
                cycle.cycleLengthDays in 21..35 -> "Normal range"
                cycle.cycleLengthDays < 21 -> "Short cycle"
                else -> "Long cycle"
            }
            DoctorCycleRow(
                cycleNumber = cycleNum,
                startDate = cycle.startDate,
                endDate = cycle.endDate,
                cycleLengthDays = cycle.cycleLengthDays,
                periodLengthDays = cycle.periodLengthDays,
                isOngoing = isOngoing,
                regularityNote = regularityNote
            )
        }

        // Filter logs
        val relevantLogs = if (startDateLimit == null) {
            allLogs
        } else {
            allLogs.filter { !it.log.date.isBefore(startDateLimit) }
        }

        // Analyze symptoms: frequency, phase correlation, severity
        val symptomOccurrences = mutableMapOf<String, MutableList<SymptomOccurrence>>()
        relevantLogs.forEach { logWithSymptoms ->
            val logDate = logWithSymptoms.log.date
            val cycle = findCycleForDate(logDate, allCycles)
            val timing = determineSymptomTiming(logDate, cycle, avgCycleLength?.roundToInt() ?: 28)

            logWithSymptoms.symptoms.forEach { sym ->
                val list = symptomOccurrences.getOrPut(sym.symptomTag) { mutableListOf() }
                list.add(
                    SymptomOccurrence(
                        date = logDate,
                        severity = sym.severityLevel,
                        phaseTiming = timing,
                        cycleId = cycle?.id
                    )
                )
            }
        }

        val totalCyclesCountForPercent = relevantCycles.size.coerceAtLeast(1)

        val symptomSummaries = symptomOccurrences.map { (tag, occurrences) ->
            val displayName = TagLookup.getSymptomById(tag)?.displayName
                ?: tag.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val totalEpisodes = occurrences.size
            val avgSeverity = occurrences.map { it.severity }.average()
            val maxSeverity = occurrences.maxOfOrNull { it.severity } ?: 1

            // Count phase occurrences
            val phaseCounts = occurrences.groupingBy { it.phaseTiming }.eachCount()
            val dominantPhaseEntry = phaseCounts.maxByOrNull { it.value }
            val dominantPhaseText = if (dominantPhaseEntry != null && totalEpisodes > 0) {
                val pct = ((dominantPhaseEntry.value.toDouble() / totalEpisodes) * 100).roundToInt()
                "${dominantPhaseEntry.key} ($pct%)"
            } else {
                "Throughout cycle"
            }

            // Cycles affected
            val distinctCycles = occurrences.mapNotNull { it.cycleId }.distinct().size
            val cyclesAffectedPct = ((distinctCycles.toDouble() / totalCyclesCountForPercent) * 100).roundToInt()
                .coerceIn(0, 100)

            DoctorSymptomSummary(
                symptomTag = tag,
                displayName = displayName,
                totalEpisodes = totalEpisodes,
                primaryPhaseOrTiming = dominantPhaseText,
                averageSeverity = avgSeverity,
                maxSeverity = maxSeverity,
                cyclesAffectedPercentage = cyclesAffectedPct
            )
        }.sortedByDescending { it.totalEpisodes }

        // Daily log rows
        val dailyLogRows = relevantLogs.map { logWithSymptoms ->
            val log = logWithSymptoms.log
            val cycle = findCycleForDate(log.date, allCycles)
            val cycleDay = if (cycle != null) {
                val d = ChronoUnit.DAYS.between(cycle.startDate, log.date) + 1
                if (d in 1..90) d.toInt() else null
            } else null

            val symptomsList = logWithSymptoms.symptoms.map {
                val name = TagLookup.getSymptomById(it.symptomTag)?.displayName
                    ?: it.symptomTag.replace('_', ' ').replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                Pair(name, it.severityLevel)
            }

            DoctorDailyLogRow(
                date = log.date,
                cycleDay = cycleDay,
                flowIntensity = log.flowIntensity,
                symptoms = symptomsList,
                moods = log.mood,
                energyLevel = log.energyLevel,
                sleepQuality = log.sleepQuality,
                bbtCelsius = log.bbtCelsius,
                sexualActivity = log.sexualActivity,
                medicationTaken = log.medicationTaken,
                notes = log.notes
            )
        }.sortedByDescending { it.date }

        DoctorReportData(
            patientName = patientName,
            generatedDate = today,
            dateRangeDescription = dateRangeDescription,
            lastPeriodDate = lastPeriodDate,
            currentCycleDay = currentCycleDay,
            averageCycleLength = avgCycleLength,
            cycleLengthVariationDays = cycleLengthSd,
            cycleRegularityText = cycleRegularityText,
            averagePeriodLength = avgPeriodLength,
            totalRecordedCycles = relevantCycles.size,
            cycles = cycleRows,
            symptomSummaries = symptomSummaries,
            dailyLogs = dailyLogRows
        )
    }

    private data class SymptomOccurrence(
        val date: LocalDate,
        val severity: Int,
        val phaseTiming: String,
        val cycleId: Long?
    )

    private fun findCycleForDate(date: LocalDate, cycles: List<CycleEntity>): CycleEntity? {
        return cycles.firstOrNull { cycle ->
            val start = cycle.startDate
            val end = cycle.endDate ?: LocalDate.now().plusDays(30)
            !date.isBefore(start) && !date.isAfter(end)
        }
    }

    private fun determineSymptomTiming(date: LocalDate, cycle: CycleEntity?, typicalLength: Int): String {
        if (cycle == null) return "General / Unspecified"
        val dayOfCycle = (ChronoUnit.DAYS.between(cycle.startDate, date) + 1).toInt()
        val cycleLen = cycle.cycleLengthDays ?: typicalLength

        return when {
            dayOfCycle in 1..cycle.periodLengthDays -> "Menstrual (Days 1–${cycle.periodLengthDays})"
            dayOfCycle >= (cycleLen - 5) -> "Premenstrual / Late Luteal"
            dayOfCycle in (cycleLen - 16)..(cycleLen - 12) -> "Mid-Cycle / Ovulatory"
            dayOfCycle in (cycle.periodLengthDays + 1)..(cycleLen - 17) -> "Follicular Phase"
            else -> "Luteal Phase"
        }
    }

    // --- CSV GENERATION ---

    fun generateCsv(reportData: DoctorReportData): String {
        val sb = StringBuilder()

        // Clinical header metadata
        sb.append("# AURA CYCLE · CLINICAL HEALTH & SYMPTOM SUMMARY\n")
        sb.append("# Generated: ${reportData.generatedDate.format(dateFormatter)}\n")
        sb.append("# Patient Name: ${escapeCsv(reportData.patientName)}\n")
        sb.append("# Reporting Period: ${reportData.dateRangeDescription}\n")
        val lmpStr = reportData.lastPeriodDate?.format(dateFormatter) ?: "None recorded"
        val lmpDayStr = reportData.currentCycleDay?.let { " (Cycle Day $it)" } ?: ""
        sb.append("# Last Menstrual Period (LMP): $lmpStr$lmpDayStr\n")
        val avgCycleStr = reportData.averageCycleLength?.let { String.format("%.1f days", it) } ?: "Insufficient data"
        val varStr = reportData.cycleLengthVariationDays?.let { String.format(" (± %.1f days)", it) } ?: ""
        sb.append("# Average Cycle Length: $avgCycleStr$varStr - ${reportData.cycleRegularityText}\n")
        val avgPeriodStr = reportData.averagePeriodLength?.let { String.format("%.1f days", it) } ?: "Insufficient data"
        sb.append("# Average Bleeding Duration: $avgPeriodStr\n")
        sb.append("# Total Tracked Cycles: ${reportData.totalRecordedCycles}\n")
        sb.append("#\n\n")

        // SECTION 1: CYCLE HISTORY
        sb.append("--- SECTION 1: MENSTRUAL CYCLE HISTORY ---\n")
        sb.append("Cycle Number,Start Date,End Date,Cycle Length (Days),Period Bleeding (Days),Status,Observations\n")
        reportData.cycles.forEach { c ->
            val endStr = c.endDate?.format(dateFormatter) ?: "Ongoing"
            val lenStr = c.cycleLengthDays?.toString() ?: "Ongoing"
            val statusStr = if (c.isOngoing) "Current" else "Completed"
            sb.append("${c.cycleNumber},${c.startDate.format(dateFormatter)},$endStr,$lenStr,${c.periodLengthDays},$statusStr,${escapeCsv(c.regularityNote)}\n")
        }
        sb.append("\n")

        // SECTION 2: SYMPTOM PATTERN & TIMING ANALYSIS
        sb.append("--- SECTION 2: SYMPTOM PATTERN & TIMING ANALYSIS (DOCTOR'S CHECKLIST) ---\n")
        sb.append("Symptom,Total Logged Episodes,Dominant Timing / Cycle Phase,Average Severity (1-3),Max Severity,Cycles Affected (%)\n")
        if (reportData.symptomSummaries.isEmpty()) {
            sb.append("No recurring symptoms logged during this reporting period,,,,,\n")
        } else {
            reportData.symptomSummaries.forEach { s ->
                val avgSevStr = String.format("%.1f", s.averageSeverity)
                val sevLabel = when {
                    s.averageSeverity >= 2.5 -> "Severe ($avgSevStr)"
                    s.averageSeverity >= 1.7 -> "Moderate ($avgSevStr)"
                    else -> "Mild ($avgSevStr)"
                }
                sb.append("${escapeCsv(s.displayName)},${s.totalEpisodes},${escapeCsv(s.primaryPhaseOrTiming)},$sevLabel,${s.maxSeverity},${s.cyclesAffectedPercentage}%\n")
            }
        }
        sb.append("\n")

        // SECTION 3: DAILY LOGS
        sb.append("--- SECTION 3: CHRONOLOGICAL DAILY LOG ENTRIES ---\n")
        sb.append("Date,Cycle Day,Flow Intensity,Symptoms Logged,Moods,Energy Level,Sleep Quality,BBT (C),Sexual Activity,Medication Taken,Notes\n")
        reportData.dailyLogs.forEach { log ->
            val dateStr = log.date.format(dateFormatter)
            val dayStr = log.cycleDay?.toString() ?: "—"
            val flowStr = if (log.flowIntensity == FlowIntensity.NONE) "None" else log.flowIntensity.name.lowercase().replaceFirstChar { it.titlecase() }
            val symptomsStr = log.symptoms.joinToString("; ") { "${it.first} (Sev ${it.second})" }
            val moodsStr = log.moods.joinToString("; ")
            val energyStr = log.energyLevel?.displayName ?: ""
            val sleepStr = log.sleepQuality?.displayName ?: ""
            val bbtStr = log.bbtCelsius?.let { String.format("%.2f", it) } ?: ""
            val sexStr = log.sexualActivity?.name?.lowercase() ?: ""
            val medStr = when (log.medicationTaken) {
                true -> "Yes"
                false -> "No"
                null -> ""
            }
            sb.append("$dateStr,$dayStr,$flowStr,${escapeCsv(symptomsStr)},${escapeCsv(moodsStr)},${escapeCsv(energyStr)},${escapeCsv(sleepStr)},$bbtStr,$sexStr,$medStr,${escapeCsv(log.notes)}\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // --- PDF GENERATION ---

    fun generatePdf(reportData: DoctorReportData): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in pt
        val pageHeight = 842 // A4 height in pt
        val marginLeft = 36f
        val marginRight = 559f
        val printableWidth = marginRight - marginLeft
        val pageBottom = 790f

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Standard Paint Objects
        val paintPrimary = Paint().apply {
            color = Color.rgb(43, 37, 35) // Deep charcoal brown
            isAntiAlias = true
        }

        val paintAccent = Paint().apply {
            color = Color.rgb(141, 78, 56) // Terracotta accent
            isAntiAlias = true
        }

        val paintMuted = Paint().apply {
            color = Color.rgb(110, 102, 96)
            isAntiAlias = true
        }

        val paintDivider = Paint().apply {
            color = Color.rgb(226, 217, 210)
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val paintCardBg = Paint().apply {
            color = Color.rgb(248, 243, 239)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintHeaderBg = Paint().apply {
            color = Color.rgb(242, 233, 227)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintAltRow = Paint().apply {
            color = Color.rgb(252, 249, 246)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var curY = 40f

        fun drawFooter() {
            canvas.drawLine(marginLeft, pageBottom - 20f, marginRight, pageBottom - 20f, paintDivider)
            paintMuted.textSize = 7.5f
            paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("Aura Cycle · Confidential Clinical Medical Summary · Strictly On-Device", marginLeft, pageBottom - 8f, paintMuted)
            val pageStr = "Page $pageNumber"
            val pageStrWidth = paintMuted.measureText(pageStr)
            canvas.drawText(pageStr, marginRight - pageStrWidth, pageBottom - 8f, paintMuted)
        }

        fun checkPageBreak(neededHeight: Float) {
            if (curY + neededHeight > pageBottom - 30f) {
                drawFooter()
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas

                // Header on continuation pages
                paintAccent.textSize = 8.5f
                paintAccent.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                canvas.drawText("AURA CYCLE · CLINICAL HEALTH SUMMARY", marginLeft, 40f, paintAccent)

                paintMuted.textSize = 8f
                paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                val contSub = "Patient: ${reportData.patientName} · Date: ${reportData.generatedDate.format(dateFormatter)}"
                canvas.drawText(contSub, marginRight - paintMuted.measureText(contSub), 40f, paintMuted)
                canvas.drawLine(marginLeft, 46f, marginRight, 46f, paintDivider)

                curY = 62f
            }
        }

        // --- PAGE 1: TITLE & CLINICAL HEADER ---
        paintAccent.textSize = 10f
        paintAccent.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("AURA CYCLE · CLINICAL HEALTH & SYMPTOM REPORT", marginLeft, curY, paintAccent)
        curY += 16f

        paintPrimary.textSize = 16f
        paintPrimary.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        canvas.drawText("Menstrual Cycle & Symptom Summary", marginLeft, curY, paintPrimary)
        curY += 14f

        paintMuted.textSize = 8.5f
        paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val infoLine = "Patient: ${reportData.patientName}  ·  Report Date: ${reportData.generatedDate.format(dateFormatter)}  ·  Period: ${reportData.dateRangeDescription}"
        canvas.drawText(infoLine, marginLeft, curY, paintMuted)
        curY += 10f

        canvas.drawLine(marginLeft, curY, marginRight, curY, paintDivider)
        curY += 14f

        // --- VITALS SUMMARY CARDS (4 boxes) ---
        val boxGap = 8f
        val boxWidth = (printableWidth - (boxGap * 3)) / 4f
        val boxHeight = 52f

        val vitals = listOf(
            Triple(
                "LAST PERIOD (LMP)",
                reportData.lastPeriodDate?.format(shortDateFormatter) ?: "None",
                reportData.currentCycleDay?.let { "Cycle Day $it" } ?: "No active cycle"
            ),
            Triple(
                "AVG CYCLE LENGTH",
                reportData.averageCycleLength?.let { String.format("%.1f d", it) } ?: "—",
                reportData.cycleLengthVariationDays?.let { String.format("±%.1f d (%s)", it, reportData.cycleRegularityText.take(8)) } ?: reportData.cycleRegularityText
            ),
            Triple(
                "AVG BLEEDING",
                reportData.averagePeriodLength?.let { String.format("%.1f d", it) } ?: "—",
                "Typical duration"
            ),
            Triple(
                "TRACKED CYCLES",
                "${reportData.totalRecordedCycles}",
                "${reportData.cycles.count { !it.isOngoing }} completed"
            )
        )

        vitals.forEachIndexed { i, (label, valText, subText) ->
            val bLeft = marginLeft + i * (boxWidth + boxGap)
            val bRight = bLeft + boxWidth
            val rect = RectF(bLeft, curY, bRight, curY + boxHeight)
            canvas.drawRoundRect(rect, 6f, 6f, paintCardBg)
            canvas.drawRoundRect(rect, 6f, 6f, paintDivider)

            paintMuted.textSize = 7f
            paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(label, bLeft + 8f, curY + 14f, paintMuted)

            paintPrimary.textSize = 12f
            paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(valText, bLeft + 8f, curY + 30f, paintPrimary)

            paintMuted.textSize = 6.8f
            paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(subText, bLeft + 8f, curY + 44f, paintMuted)
        }

        curY += boxHeight + 16f

        // --- SECTION 1: MENSTRUAL CYCLE HISTORY ---
        checkPageBreak(80f)
        paintAccent.textSize = 10.5f
        paintAccent.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("1. Menstrual Cycle History", marginLeft, curY, paintAccent)
        curY += 12f

        val colC1 = marginLeft
        val colC2 = marginLeft + 50f
        val colC3 = marginLeft + 140f
        val colC4 = marginLeft + 230f
        val colC5 = marginLeft + 320f
        val colC6 = marginLeft + 410f

        // Table Header
        canvas.drawRect(marginLeft, curY, marginRight, curY + 16f, paintHeaderBg)
        paintPrimary.textSize = 7.5f
        paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("CYCLE", colC1 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("START DATE", colC2 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("END DATE", colC3 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("LENGTH", colC4 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("BLEEDING", colC5 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("STATUS / NOTES", colC6 + 4f, curY + 11f, paintPrimary)
        curY += 16f

        paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paintPrimary.textSize = 8f

        reportData.cycles.take(12).forEachIndexed { idx, cycle ->
            checkPageBreak(16f)
            if (idx % 2 == 1) {
                canvas.drawRect(marginLeft, curY, marginRight, curY + 15f, paintAltRow)
            }
            canvas.drawText("#${cycle.cycleNumber}", colC1 + 4f, curY + 11f, paintPrimary)
            canvas.drawText(cycle.startDate.format(dateFormatter), colC2 + 4f, curY + 11f, paintPrimary)
            canvas.drawText(cycle.endDate?.format(dateFormatter) ?: "Current / Ongoing", colC3 + 4f, curY + 11f, paintPrimary)
            canvas.drawText(cycle.cycleLengthDays?.let { "$it days" } ?: "Ongoing", colC4 + 4f, curY + 11f, paintPrimary)
            canvas.drawText("${cycle.periodLengthDays} days", colC5 + 4f, curY + 11f, paintPrimary)
            canvas.drawText(cycle.regularityNote, colC6 + 4f, curY + 11f, paintPrimary)
            curY += 15f
        }

        curY += 16f

        // --- SECTION 2: SYMPTOM PATTERN & TIMING ANALYSIS ---
        checkPageBreak(80f)
        paintAccent.textSize = 10.5f
        paintAccent.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("2. Symptom Pattern & Timing Analysis (Clinical Checklist)", marginLeft, curY, paintAccent)
        curY += 5f

        paintMuted.textSize = 7.5f
        paintMuted.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Answers clinical questions regarding recurring pain, migraines, and cycle phase clustering:", marginLeft, curY + 8f, paintMuted)
        curY += 14f

        val colS1 = marginLeft
        val colS2 = marginLeft + 120f
        val colS3 = marginLeft + 175f
        val colS4 = marginLeft + 335f
        val colS5 = marginLeft + 420f

        // Table Header
        canvas.drawRect(marginLeft, curY, marginRight, curY + 16f, paintHeaderBg)
        paintPrimary.textSize = 7.5f
        paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("SYMPTOM", colS1 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("EPISODES", colS2 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("PRIMARY CYCLE TIMING / PHASE", colS3 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("AVG SEVERITY", colS4 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("CYCLES AFFECTED", colS5 + 4f, curY + 11f, paintPrimary)
        curY += 16f

        if (reportData.symptomSummaries.isEmpty()) {
            checkPageBreak(20f)
            paintMuted.textSize = 8f
            canvas.drawText("No recurring symptoms logged during this reporting period.", marginLeft + 8f, curY + 12f, paintMuted)
            curY += 18f
        } else {
            paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            paintPrimary.textSize = 8f

            reportData.symptomSummaries.forEachIndexed { idx, sym ->
                checkPageBreak(16f)
                if (idx % 2 == 1) {
                    canvas.drawRect(marginLeft, curY, marginRight, curY + 15f, paintAltRow)
                }
                val sevDesc = when {
                    sym.averageSeverity >= 2.5 -> "Severe (${String.format("%.1f", sym.averageSeverity)}/3)"
                    sym.averageSeverity >= 1.7 -> "Moderate (${String.format("%.1f", sym.averageSeverity)}/3)"
                    else -> "Mild (${String.format("%.1f", sym.averageSeverity)}/3)"
                }

                canvas.drawText(sym.displayName, colS1 + 4f, curY + 11f, paintPrimary)
                canvas.drawText("${sym.totalEpisodes} times", colS2 + 4f, curY + 11f, paintPrimary)
                canvas.drawText(sym.primaryPhaseOrTiming, colS3 + 4f, curY + 11f, paintPrimary)
                canvas.drawText(sevDesc, colS4 + 4f, curY + 11f, paintPrimary)
                canvas.drawText("${sym.cyclesAffectedPercentage}% of cycles", colS5 + 4f, curY + 11f, paintPrimary)
                curY += 15f
            }
        }

        curY += 16f

        // --- SECTION 3: RECENT DAILY OBSERVATIONS ---
        checkPageBreak(80f)
        paintAccent.textSize = 10.5f
        paintAccent.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("3. Detailed Daily Log Entries (Sample / Chronological)", marginLeft, curY, paintAccent)
        curY += 12f

        val colD1 = marginLeft
        val colD2 = marginLeft + 75f
        val colD3 = marginLeft + 130f
        val colD4 = marginLeft + 190f
        val colD5 = marginLeft + 370f

        canvas.drawRect(marginLeft, curY, marginRight, curY + 16f, paintHeaderBg)
        paintPrimary.textSize = 7.5f
        paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("DATE", colD1 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("CYCLE DAY", colD2 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("FLOW", colD3 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("SYMPTOMS & MOODS", colD4 + 4f, curY + 11f, paintPrimary)
        canvas.drawText("NOTES / MEDICATION", colD5 + 4f, curY + 11f, paintPrimary)
        curY += 16f

        val logSample = reportData.dailyLogs.take(30)
        if (logSample.isEmpty()) {
            checkPageBreak(20f)
            paintMuted.textSize = 8f
            canvas.drawText("No daily entries recorded for this time frame.", marginLeft + 8f, curY + 12f, paintMuted)
            curY += 18f
        } else {
            paintPrimary.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            paintPrimary.textSize = 7.5f

            logSample.forEachIndexed { idx, log ->
                checkPageBreak(15f)
                if (idx % 2 == 1) {
                    canvas.drawRect(marginLeft, curY, marginRight, curY + 14f, paintAltRow)
                }

                val dateStr = log.date.format(shortDateFormatter)
                val dayStr = log.cycleDay?.let { "Day $it" } ?: "—"
                val flowStr = if (log.flowIntensity == FlowIntensity.NONE) "None" else log.flowIntensity.name.lowercase().replaceFirstChar { it.titlecase() }

                val sympMood = mutableListOf<String>()
                if (log.symptoms.isNotEmpty()) {
                    sympMood.add(log.symptoms.joinToString { it.first })
                }
                if (log.moods.isNotEmpty()) {
                    sympMood.add(log.moods.joinToString())
                }
                val sympMoodStr = sympMood.joinToString(" | ").take(42)

                val notesMed = buildString {
                    if (log.medicationTaken == true) append("[Med] ")
                    if (log.notes.isNotBlank()) append(log.notes)
                }.take(32)

                canvas.drawText(dateStr, colD1 + 4f, curY + 10f, paintPrimary)
                canvas.drawText(dayStr, colD2 + 4f, curY + 10f, paintPrimary)
                canvas.drawText(flowStr, colD3 + 4f, curY + 10f, paintPrimary)
                canvas.drawText(sympMoodStr, colD4 + 4f, curY + 10f, paintPrimary)
                canvas.drawText(notesMed, colD5 + 4f, curY + 10f, paintPrimary)

                curY += 14f
            }
        }

        // Draw footer on last page
        drawFooter()
        document.finishPage(page)

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    // --- SHARE & EXPORT ACTIONS ---

    suspend fun createShareableReport(
        context: Context,
        format: ReportFormat,
        rangeMonths: Int? = 6
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        try {
            val reportData = buildReportData(rangeMonths)
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val fileName = "aura_doctor_report_${LocalDate.now()}.${format.extension}"
            val file = File(exportDir, fileName)

            when (format) {
                ReportFormat.PDF -> {
                    val pdfBytes = generatePdf(reportData)
                    FileOutputStream(file).use { it.write(pdfBytes) }
                }
                ReportFormat.CSV -> {
                    val csvText = generateCsv(reportData)
                    file.writeText(csvText, Charsets.UTF_8)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(Pair(uri, fileName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportReportToOutputStream(
        outputStream: OutputStream,
        format: ReportFormat,
        rangeMonths: Int? = 6
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reportData = buildReportData(rangeMonths)
            when (format) {
                ReportFormat.PDF -> {
                    val pdfBytes = generatePdf(reportData)
                    outputStream.use { it.write(pdfBytes) }
                }
                ReportFormat.CSV -> {
                    val csvText = generateCsv(reportData)
                    outputStream.use { it.write(csvText.toByteArray(Charsets.UTF_8)) }
                }
            }
            Result.success("Export completed successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
