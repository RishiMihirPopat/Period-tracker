package com.example.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.local.CycleDatabase
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.model.FlowIntensity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CycleWidgetDataResolverTest {

    private lateinit var context: Context
    private lateinit var database: CycleDatabase
    private lateinit var resolver: CycleWidgetDataResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CycleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resolver = CycleWidgetDataResolver(context, database = database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty database returns friendly cold start tracking state`() = runBlocking {
        val widgetData = resolver.resolveWidgetData(LocalDate.of(2026, 9, 15))

        assertFalse(widgetData.isTracking)
        assertEquals("Day 1", widgetData.cycleDayText)
        assertEquals("Start Tracking", widgetData.phaseName)
        assertEquals("Tap + to log your period", widgetData.milestoneText)
        assertEquals(R.drawable.widget_pill_neutral, widgetData.phasePillDrawable)
        assertEquals(R.color.widget_phase_neutral_text, widgetData.phaseTextColor)
        assertEquals(R.drawable.widget_dot_neutral, widgetData.phaseDotDrawable)
        assertEquals("Not logged today", widgetData.todayLogText)
    }

    @Test
    fun `active cycle returns cycle day and milestone information`() = runBlocking {
        val today = LocalDate.of(2026, 9, 15)
        val cycleStart = today.minusDays(13) // Day 14

        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart,
                endDate = null,
                periodLengthDays = 5,
                cycleLengthDays = null
            )
        )
        // Add 2 completed cycles so predictions are fully activated
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(28),
                endDate = cycleStart.minusDays(1),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(56),
                endDate = cycleStart.minusDays(29),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )

        // Today's log with medium flow
        database.dailyLogDao().insertOrUpdate(
            DailyLogEntity(
                date = today,
                flowIntensity = FlowIntensity.MEDIUM
            )
        )

        val widgetData = resolver.resolveWidgetData(today)

        assertTrue(widgetData.isTracking)
        assertEquals("Day 14", widgetData.cycleDayText)
        assertNotNull(widgetData.phaseName)
        assertTrue(widgetData.milestoneText.startsWith("Next period"))
        assertEquals("Logged today: Medium flow", widgetData.todayLogText)
        assertTrue(widgetData.progressPercent in 1..100)
    }

    @Test
    fun `widget provider updateAllWidgets executes without throwing`() {
        CycleWidgetProvider.updateAllWidgets(context)
    }

    @Test
    fun `widget data resolves properly on leap day February 29 2024`() = runBlocking {
        val leapDay = LocalDate.of(2024, 2, 29)
        val cycleStart = LocalDate.of(2024, 2, 15) // Day 15 on leap day

        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart,
                endDate = null,
                periodLengthDays = 5,
                cycleLengthDays = null
            )
        )
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(28),
                endDate = cycleStart.minusDays(1),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(56),
                endDate = cycleStart.minusDays(29),
                periodLengthDays = 5,
                cycleLengthDays = 28
            )
        )

        val widgetData = resolver.resolveWidgetData(leapDay)

        assertTrue(widgetData.isTracking)
        assertEquals("Day 15", widgetData.cycleDayText)
        assertTrue(widgetData.phaseName.isNotBlank())
        assertTrue(widgetData.milestoneText.contains("Next period in 15 days"))
    }

    @Test
    fun `widget data resolves across year boundary from December 31 to January 1`() = runBlocking {
        val cycleStart = LocalDate.of(2025, 12, 22) // Day 10 on Dec 31, Day 11 on Jan 1
        val nye = LocalDate.of(2025, 12, 31)
        val nyd = LocalDate.of(2026, 1, 1)

        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart,
                endDate = null,
                periodLengthDays = 5,
                cycleLengthDays = null
            )
        )
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(29),
                endDate = cycleStart.minusDays(1),
                periodLengthDays = 5,
                cycleLengthDays = 29
            )
        )
        database.cycleDao().insertCycle(
            CycleEntity(
                startDate = cycleStart.minusDays(58),
                endDate = cycleStart.minusDays(30),
                periodLengthDays = 5,
                cycleLengthDays = 29
            )
        )

        val nyeData = resolver.resolveWidgetData(nye)
        assertTrue(nyeData.isTracking)
        assertEquals("Day 10", nyeData.cycleDayText)

        val nydData = resolver.resolveWidgetData(nyd)
        assertTrue(nydData.isTracking)
        assertEquals("Day 11", nydData.cycleDayText)
    }
}
