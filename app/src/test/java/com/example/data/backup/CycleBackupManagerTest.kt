package com.example.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CycleDatabase
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.local.entity.UserSettingsEntity
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CycleBackupManagerTest {

    private lateinit var context: Context
    private lateinit var database: CycleDatabase
    private lateinit var backupManager: CycleBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CycleDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        backupManager = CycleBackupManager(
            database = database,
            cycleDao = database.cycleDao(),
            dailyLogDao = database.dailyLogDao(),
            symptomLogDao = database.symptomLogDao(),
            userSettingsDao = database.userSettingsDao(),
            profileDao = database.profileDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `encrypted backup export and decrypt roundtrip preserves database structure`() = runBlocking {
        // Populate sample data
        val cycle = CycleEntity(
            id = 1,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 29),
            periodLengthDays = 5,
            cycleLengthDays = 28
        )
        database.cycleDao().insertCycle(cycle)

        val log = DailyLogEntity(
            date = LocalDate.of(2026, 8, 1),
            cycleId = 1,
            flowIntensity = FlowIntensity.MEDIUM,
            mood = listOf("Calm", "Reflective"),
            notes = "First day of cycle"
        )
        database.dailyLogDao().insertOrUpdate(log)

        val symptom = SymptomLogEntity(
            id = 0,
            dailyLogId = LocalDate.of(2026, 8, 1),
            symptomTag = "cramps",
            severityLevel = 3
        )
        database.symptomLogDao().insertSymptoms(listOf(symptom))

        val profile = ProfileEntity(id = 1, name = "Sarah", onboardingCompletedAt = 123456789L)
        database.profileDao().insertOrUpdateProfile(profile)

        // 1. Export with passphrase
        val passphrase = "SecurePassword123!"
        val (encryptedBytes, metadata) = backupManager.createEncryptedBackupBytes(passphrase).getOrThrow()

        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.isNotEmpty())
        assertEquals(1, metadata.cyclesCount)
        assertEquals(1, metadata.logsCount)
        assertEquals(1, metadata.symptomsCount)

        // 2. Decrypt with correct passphrase
        val decryptedJson = backupManager.decryptBackup(ByteArrayInputStream(encryptedBytes), passphrase).getOrThrow()
        assertEquals("Aura Cycle", decryptedJson.getString("app"))
        assertEquals(1, decryptedJson.getJSONArray("cycles").length())
        assertEquals(1, decryptedJson.getJSONArray("daily_logs").length())
        assertEquals(1, decryptedJson.getJSONArray("symptoms").length())
        assertEquals("Sarah", decryptedJson.getJSONObject("profile").getString("name"))

        val firstCycle = decryptedJson.getJSONArray("cycles").getJSONObject(0)
        assertEquals("2026-08-01", firstCycle.getString("startDate"))
        assertEquals(28, firstCycle.getInt("cycleLengthDays"))
    }

    @Test
    fun `decryption fails with SecurityException when wrong passphrase is provided`() = runBlocking {
        val (encryptedBytes, _) = backupManager.createEncryptedBackupBytes("CorrectPassword999").getOrThrow()

        val result = backupManager.decryptBackup(ByteArrayInputStream(encryptedBytes), "WrongPassword123")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception?.message?.contains("Incorrect password") == true)
    }

    @Test
    fun `decryption fails with IllegalArgumentException on non-Aura header or corrupt bytes`() = runBlocking {
        val fakeBytes = "This is not an encrypted Aura backup file".toByteArray(Charsets.UTF_8)
        val result = backupManager.decryptBackup(ByteArrayInputStream(fakeBytes), "AnyPassword")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `full database restore wipes existing data and restores backup snapshot atomically`() = runBlocking {
        // Seed initial cycle
        val initialCycle = CycleEntity(
            id = 10,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 28),
            periodLengthDays = 4,
            cycleLengthDays = 27
        )
        database.cycleDao().insertCycle(initialCycle)

        val initialLog = DailyLogEntity(
            date = LocalDate.of(2026, 7, 1),
            cycleId = 10,
            flowIntensity = FlowIntensity.HEAVY,
            mood = listOf("Tired"),
            notes = "Old log"
        )
        database.dailyLogDao().insertOrUpdate(initialLog)

        // Create backup
        val passphrase = "VaultPassword456"
        val (backupBytes, _) = backupManager.createEncryptedBackupBytes(passphrase).getOrThrow()

        // Wipe or change database to simulate fresh/wiped device
        database.cycleDao().deleteAllCycles()
        database.dailyLogDao().deleteAllDailyLogs()
        assertEquals(0, database.cycleDao().getAllCyclesSync().size)
        assertEquals(0, database.dailyLogDao().getAllDailyLogsSync().size)

        // Restore from backup
        val restoreResult = backupManager.restoreDatabase(ByteArrayInputStream(backupBytes), passphrase).getOrThrow()
        assertEquals(1, restoreResult.cyclesRestored)
        assertEquals(1, restoreResult.logsRestored)

        // Verify database contents
        val restoredCycles = database.cycleDao().getAllCyclesSync()
        val restoredLogs = database.dailyLogDao().getAllDailyLogsSync()

        assertEquals(1, restoredCycles.size)
        assertEquals(LocalDate.of(2026, 7, 1), restoredCycles[0].startDate)
        assertEquals(27, restoredCycles[0].cycleLengthDays)

        assertEquals(1, restoredLogs.size)
        assertEquals("Old log", restoredLogs[0].notes)
        assertEquals(FlowIntensity.HEAVY, restoredLogs[0].flowIntensity)
    }

    @Test
    fun `exportToOutputStream writes full valid backup`() = runBlocking {
        val outStream = ByteArrayOutputStream()
        val meta = backupManager.exportToOutputStream(outStream, "TestPassphrase123").getOrThrow()

        assertNotNull(meta)
        val writtenBytes = outStream.toByteArray()
        assertTrue(writtenBytes.isNotEmpty())

        val decrypted = backupManager.decryptBackup(ByteArrayInputStream(writtenBytes), "TestPassphrase123").getOrThrow()
        assertEquals("Aura Cycle", decrypted.getString("app"))
    }
}
