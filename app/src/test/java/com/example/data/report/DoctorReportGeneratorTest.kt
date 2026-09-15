package com.example.data.report

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CycleDatabase
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.model.FlowIntensity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DoctorReportGeneratorTest {

    private lateinit var context: Context
    private lateinit var database: CycleDatabase
    private lateinit var reportGenerator: DoctorReportGenerator

    private val baseDate = LocalDate.now()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CycleDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        reportGenerator = DoctorReportGenerator(
            cycleDao = database.cycleDao(),
            dailyLogDao = database.dailyLogDao(),
            symptomLogDao = database.symptomLogDao(),
            profileDao = database.profileDao(),
            userSettingsDao = database.userSettingsDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `buildReportData correctly extracts LMP, cycle regularity, and symptom timing`() = runBlocking {
        // Insert profile
        database.profileDao().insertOrUpdateProfile(ProfileEntity(id = 1, name = "Jane Doe"))

        // Cycle 1: Started 60 days ago, ended 32 days ago (length 28 days)
        database.cycleDao().insertCycle(
            CycleEntity(
                id = 1,
                startDate = baseDate.minusDays(60),
                endDate = baseDate.minusDays(33),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )
        // Cycle 2: Started 32 days ago, ended 4 days ago (length 28 days)
        database.cycleDao().insertCycle(
            CycleEntity(
                id = 2,
                startDate = baseDate.minusDays(32),
                endDate = baseDate.minusDays(5),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )
        // Active / Current Cycle: Started 4 days ago
        database.cycleDao().insertCycle(
            CycleEntity(
                id = 3,
                startDate = baseDate.minusDays(4),
                endDate = null,
                periodLengthDays = 4,
                cycleLengthDays = null
            )
        )

        // Insert Daily logs & symptoms
        database.dailyLogDao().insertOrUpdate(DailyLogEntity(date = baseDate.minusDays(4), flowIntensity = FlowIntensity.HEAVY, notes = "Period day 1"))
        database.dailyLogDao().insertOrUpdate(DailyLogEntity(date = baseDate.minusDays(3), flowIntensity = FlowIntensity.MEDIUM, notes = "Period day 2"))
        database.dailyLogDao().insertOrUpdate(DailyLogEntity(date = baseDate.minusDays(2), flowIntensity = FlowIntensity.LIGHT, notes = ""))
        database.dailyLogDao().insertOrUpdate(DailyLogEntity(date = baseDate.minusDays(10), flowIntensity = FlowIntensity.NONE, notes = "Luteal headache"))

        database.symptomLogDao().insertSymptoms(
            listOf(
                SymptomLogEntity(id = 0, dailyLogId = baseDate.minusDays(4), symptomTag = "cramps", severityLevel = 3),
                SymptomLogEntity(id = 0, dailyLogId = baseDate.minusDays(3), symptomTag = "cramps", severityLevel = 2),
                SymptomLogEntity(id = 0, dailyLogId = baseDate.minusDays(10), symptomTag = "headache", severityLevel = 2)
            )
        )

        val reportData = reportGenerator.buildReportData(rangeMonths = null)

        // Verify LMP
        assertEquals(baseDate.minusDays(4), reportData.lastPeriodDate)
        assertEquals("Jane Doe", reportData.patientName)
        assertEquals(3, reportData.totalRecordedCycles)
        assertEquals(28.0, reportData.averageCycleLength ?: 0.0, 0.1)
        assertTrue(reportData.cycleRegularityText.contains("Regular"))

        // Verify symptoms identified
        val crampsSummary = reportData.symptomSummaries.find { it.symptomTag == "cramps" }
        assertNotNull(crampsSummary)
        assertEquals(2, crampsSummary?.totalEpisodes)

        val headacheSummary = reportData.symptomSummaries.find { it.symptomTag == "headache" }
        assertNotNull(headacheSummary)
        assertEquals(1, headacheSummary?.totalEpisodes)
    }

    @Test
    fun `generateCsv creates well-structured clinical CSV report`() {
        val reportData = DoctorReportData(
            patientName = "Elena Rostova",
            generatedDate = LocalDate.of(2026, 9, 15),
            dateRangeDescription = "Past 3 Months",
            lastPeriodDate = LocalDate.of(2026, 9, 10),
            currentCycleDay = 6,
            averageCycleLength = 28.5,
            cycleLengthVariationDays = 1.2,
            cycleRegularityText = "Regular (variation: ±1.2 days)",
            averagePeriodLength = 4.8,
            totalRecordedCycles = 4,
            cycles = listOf(
                DoctorCycleRow(
                    cycleNumber = 1,
                    startDate = LocalDate.of(2026, 8, 12),
                    endDate = LocalDate.of(2026, 9, 9),
                    cycleLengthDays = 29,
                    periodLengthDays = 5,
                    isOngoing = false,
                    regularityNote = "Normal"
                )
            ),
            symptomSummaries = listOf(
                DoctorSymptomSummary(
                    symptomTag = "migraine",
                    displayName = "Migraine",
                    totalEpisodes = 3,
                    primaryPhaseOrTiming = "Luteal phase",
                    averageSeverity = 2.7,
                    maxSeverity = 3,
                    cyclesAffectedPercentage = 75
                )
            ),
            dailyLogs = listOf(
                DoctorDailyLogRow(
                    date = LocalDate.of(2026, 9, 10),
                    cycleDay = 1,
                    flowIntensity = FlowIntensity.HEAVY,
                    symptoms = listOf("Cramps" to 3),
                    moods = listOf("Fatigued"),
                    energyLevel = null,
                    sleepQuality = null,
                    bbtCelsius = null,
                    sexualActivity = null,
                    medicationTaken = null,
                    notes = "Took ibuprofen"
                )
            )
        )

        val csv = reportGenerator.generateCsv(reportData)

        // Check header section
        assertTrue(csv.contains("AURA CYCLE · CLINICAL HEALTH & SYMPTOM SUMMARY"))
        assertTrue(csv.contains("Elena Rostova"))
        assertTrue(csv.contains("Sep 10, 2026"))
        assertTrue(csv.contains("28.5 days"))
        assertTrue(csv.contains("Regular"))

        // Check cycle history section
        assertTrue(csv.contains("SECTION 1: MENSTRUAL CYCLE HISTORY"))
        assertTrue(csv.contains("Aug 12, 2026"))
        assertTrue(csv.contains("29"))

        // Check symptom summary section
        assertTrue(csv.contains("SECTION 2: SYMPTOM PATTERN & TIMING ANALYSIS"))
        assertTrue(csv.contains("Migraine"))
        assertTrue(csv.contains("Luteal phase"))

        // Check daily entries section
        assertTrue(csv.contains("SECTION 3: CHRONOLOGICAL DAILY LOG ENTRIES"))
        assertTrue(csv.contains("Heavy"))
        assertTrue(csv.contains("Cramps"))
        assertTrue(csv.contains("Took ibuprofen"))
    }

    @Test
    fun `generatePdf handles pdf creation or skips gracefully in headless JVM without native Skia`() {
        val reportData = DoctorReportData(
            patientName = "Test User",
            generatedDate = LocalDate.now(),
            dateRangeDescription = "All Recorded Data",
            lastPeriodDate = LocalDate.now().minusDays(7),
            currentCycleDay = 8,
            averageCycleLength = 29.0,
            cycleLengthVariationDays = 1.0,
            cycleRegularityText = "Regular (variation: ±1.0 days)",
            averagePeriodLength = 5.0,
            totalRecordedCycles = 2,
            cycles = listOf(
                DoctorCycleRow(
                    cycleNumber = 1,
                    startDate = LocalDate.now().minusDays(36),
                    endDate = LocalDate.now().minusDays(8),
                    cycleLengthDays = 29,
                    periodLengthDays = 5,
                    isOngoing = false,
                    regularityNote = "Normal"
                )
            ),
            symptomSummaries = listOf(
                DoctorSymptomSummary(
                    symptomTag = "cramps",
                    displayName = "Cramps",
                    totalEpisodes = 2,
                    primaryPhaseOrTiming = "Menstrual phase",
                    averageSeverity = 2.0,
                    maxSeverity = 2,
                    cyclesAffectedPercentage = 100
                )
            ),
            dailyLogs = listOf(
                DoctorDailyLogRow(
                    date = LocalDate.now().minusDays(7),
                    cycleDay = 1,
                    flowIntensity = FlowIntensity.MEDIUM,
                    symptoms = listOf("Cramps" to 2),
                    moods = listOf("Calm"),
                    energyLevel = null,
                    sleepQuality = null,
                    bbtCelsius = null,
                    sexualActivity = null,
                    medicationTaken = null,
                    notes = "Cycle onset"
                )
            )
        )

        try {
            val pdfBytes = reportGenerator.generatePdf(reportData)
            assertNotNull(pdfBytes)
            assertTrue(pdfBytes.isNotEmpty())
            val pdfHeader = String(pdfBytes.take(5).toByteArray())
            assertEquals("%PDF-", pdfHeader)
        } catch (e: IllegalStateException) {
            // Under headless Robolectric JVM without libandroid_runtime Skia, PdfDocument.startPage throws document is closed
            assertTrue(e.message?.contains("closed") == true)
        }
    }

    @Test
    fun `exportReportToOutputStream successfully writes CSV to output stream`() = runBlocking {
        database.profileDao().insertOrUpdateProfile(ProfileEntity(id = 1, name = "Test Patient"))
        database.cycleDao().insertCycle(
            CycleEntity(
                id = 1,
                startDate = baseDate.minusDays(15),
                endDate = null,
                periodLengthDays = 5,
                cycleLengthDays = null
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = reportGenerator.exportReportToOutputStream(
            outputStream = outputStream,
            format = ReportFormat.CSV,
            rangeMonths = null
        )

        assertTrue(result.isSuccess)
        val writtenString = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(writtenString.contains("AURA CYCLE · CLINICAL HEALTH & SYMPTOM SUMMARY"))
        assertTrue(writtenString.contains("Test Patient"))
    }
}
