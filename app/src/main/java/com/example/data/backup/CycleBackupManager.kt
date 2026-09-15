package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.CycleDatabase
import com.example.data.local.dao.CycleDao
import com.example.data.local.dao.DailyLogDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SymptomLogDao
import com.example.data.local.dao.UserSettingsDao
import com.example.data.local.entity.CycleEntity
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SymptomLogEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupMetadata(
    val exportTimestamp: String,
    val appIdentifier: String,
    val cyclesCount: Int,
    val logsCount: Int,
    val symptomsCount: Int,
    val earliestLogDate: String?,
    val latestLogDate: String?
)

data class BackupRestoreResult(
    val cyclesRestored: Int,
    val logsRestored: Int,
    val symptomsRestored: Int,
    val exportTimestamp: String
)

class CycleBackupManager(
    private val database: CycleDatabase,
    private val cycleDao: CycleDao,
    private val dailyLogDao: DailyLogDao,
    private val symptomLogDao: SymptomLogDao,
    private val userSettingsDao: UserSettingsDao,
    private val profileDao: ProfileDao
) {

    companion object {
        private const val MAGIC_HEADER = "AURA_ENC_V1"
        private val MAGIC_BYTES = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
    }

    /**
     * Builds an unencrypted JSON snapshot of the local database.
     */
    suspend fun generateDatabaseJson(): JSONObject = withContext(Dispatchers.IO) {
        val cycles = cycleDao.getAllCyclesSync()
        val logs = dailyLogDao.getAllDailyLogsSync()
        val symptoms = symptomLogDao.getAllSymptomsSync()
        val settings = userSettingsDao.getSettingsSync()
        val profile = profileDao.getProfileSync()

        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "Aura Cycle")
        root.put("exported_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

        // Cycles array
        val cyclesArray = JSONArray()
        for (c in cycles) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("startDate", c.startDate.toString())
            if (c.endDate != null) obj.put("endDate", c.endDate.toString())
            obj.put("periodLengthDays", c.periodLengthDays)
            if (c.cycleLengthDays != null) obj.put("cycleLengthDays", c.cycleLengthDays)
            cyclesArray.put(obj)
        }
        root.put("cycles", cyclesArray)

        // Daily logs array
        val logsArray = JSONArray()
        for (l in logs) {
            val obj = JSONObject()
            obj.put("date", l.date.toString())
            if (l.cycleId != null) obj.put("cycleId", l.cycleId)
            obj.put("flowIntensity", l.flowIntensity.name)
            obj.put("mood", JSONArray(l.mood))
            obj.put("notes", l.notes)
            if (l.energyLevel != null) obj.put("energyLevel", l.energyLevel.name)
            if (l.sleepQuality != null) obj.put("sleepQuality", l.sleepQuality.name)
            if (l.bbtCelsius != null) obj.put("bbtCelsius", l.bbtCelsius)
            if (l.sexualActivity != null) obj.put("sexualActivity", l.sexualActivity.name)
            if (l.medicationTaken != null) obj.put("medicationTaken", l.medicationTaken)
            obj.put("updatedAt", l.updatedAt)
            logsArray.put(obj)
        }
        root.put("daily_logs", logsArray)

        // Symptoms array
        val symptomsArray = JSONArray()
        for (s in symptoms) {
            val obj = JSONObject()
            obj.put("dailyLogId", s.dailyLogId.toString())
            obj.put("symptomTag", s.symptomTag)
            obj.put("severityLevel", s.severityLevel)
            symptomsArray.put(obj)
        }
        root.put("symptoms", symptomsArray)

        // Settings
        if (settings != null) {
            val sObj = JSONObject()
            sObj.put("lutealPhaseLengthDefault", settings.lutealPhaseLengthDefault)
            sObj.put("periodLengthDefault", settings.periodLengthDefault)
            sObj.put("cycleLengthDefault", settings.cycleLengthDefault)
            sObj.put("biometricEnabled", settings.biometricEnabled)
            if (settings.pinHash != null) sObj.put("pinHash", settings.pinHash)
            sObj.put("notificationsEnabled", settings.notificationsEnabled)
            sObj.put("fertileWindowAlertEnabled", settings.fertileWindowAlertEnabled)
            sObj.put("dailyDigestAlertEnabled", settings.dailyDigestAlertEnabled)
            sObj.put("phaseChangeAlertEnabled", settings.phaseChangeAlertEnabled)
            sObj.put("accentColor", settings.accentColor)
            root.put("user_settings", sObj)
        }

        // Profile
        if (profile != null) {
            val pObj = JSONObject()
            pObj.put("name", profile.name)
            if (profile.onboardingCompletedAt != null) {
                pObj.put("onboardingCompletedAt", profile.onboardingCompletedAt)
            }
            root.put("profile", pObj)
        }

        root
    }

    /**
     * Encrypts the local database JSON with AES-256-GCM derived via PBKDF2.
     */
    suspend fun createEncryptedBackupBytes(passphrase: String): Result<Pair<ByteArray, BackupMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            require(passphrase.length >= 4) { "Password must be at least 4 characters." }

            val json = generateDatabaseJson()
            val plaintext = json.toString(2).toByteArray(Charsets.UTF_8)

            val cyclesCount = json.getJSONArray("cycles").length()
            val logsCount = json.getJSONArray("daily_logs").length()
            val symptomsCount = json.getJSONArray("symptoms").length()

            val random = SecureRandom()
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)

            val totalSize = MAGIC_BYTES.size + salt.size + iv.size + ciphertext.size
            val encryptedBytes = ByteArray(totalSize)

            var offset = 0
            System.arraycopy(MAGIC_BYTES, 0, encryptedBytes, offset, MAGIC_BYTES.size)
            offset += MAGIC_BYTES.size
            System.arraycopy(salt, 0, encryptedBytes, offset, salt.size)
            offset += salt.size
            System.arraycopy(iv, 0, encryptedBytes, offset, iv.size)
            offset += iv.size
            System.arraycopy(ciphertext, 0, encryptedBytes, offset, ciphertext.size)

            val metadata = BackupMetadata(
                exportTimestamp = json.getString("exported_at"),
                appIdentifier = "Aura Cycle",
                cyclesCount = cyclesCount,
                logsCount = logsCount,
                symptomsCount = symptomsCount,
                earliestLogDate = null,
                latestLogDate = null
            )

            Pair(encryptedBytes, metadata)
        }
    }

    /**
     * Exports encrypted backup directly to an OutputStream (e.g. Storage Access Framework Uri).
     */
    suspend fun exportToOutputStream(outputStream: OutputStream, passphrase: String): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val (bytes, metadata) = createEncryptedBackupBytes(passphrase).getOrThrow()
            outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }
            metadata
        }
    }

    /**
     * Exports encrypted backup to a shareable cache file for sharing via system share sheet.
     */
    suspend fun createShareableBackup(context: Context, passphrase: String): Result<Pair<Uri, BackupMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val (bytes, metadata) = createEncryptedBackupBytes(passphrase).getOrThrow()
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val dateStr = LocalDate.now().toString()
            val file = File(exportDir, "aura_cycle_backup_$dateStr.aurabackup")
            file.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Pair(uri, metadata)
        }
    }

    /**
     * Reads and decrypts an encrypted backup file from an InputStream.
     */
    suspend fun decryptBackup(inputStream: InputStream, passphrase: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedBytes = inputStream.use { it.readBytes() }
            require(encryptedBytes.size >= MAGIC_BYTES.size + SALT_BYTES + IV_BYTES + 16) {
                "The selected file is not a valid Aura backup or is corrupted."
            }

            // Verify Magic Header
            val header = ByteArray(MAGIC_BYTES.size)
            System.arraycopy(encryptedBytes, 0, header, 0, MAGIC_BYTES.size)
            if (!header.contentEquals(MAGIC_BYTES)) {
                throw IllegalArgumentException("Unrecognized file format. Please select an .aurabackup file.")
            }

            var offset = MAGIC_BYTES.size
            val salt = ByteArray(SALT_BYTES)
            System.arraycopy(encryptedBytes, offset, salt, 0, SALT_BYTES)
            offset += SALT_BYTES

            val iv = ByteArray(IV_BYTES)
            System.arraycopy(encryptedBytes, offset, iv, 0, IV_BYTES)
            offset += IV_BYTES

            val ciphertextLen = encryptedBytes.size - offset
            val ciphertext = ByteArray(ciphertextLen)
            System.arraycopy(encryptedBytes, offset, ciphertext, 0, ciphertextLen)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            val decryptedBytes = try {
                cipher.doFinal(ciphertext)
            } catch (e: AEADBadTagException) {
                throw SecurityException("Incorrect password. Please verify your password and try again.")
            } catch (e: Exception) {
                throw SecurityException("Failed to decrypt backup: ${e.message ?: "invalid credentials"}")
            }

            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            JSONObject(jsonStr)
        }
    }

    /**
     * Inspects a backup file without modifying the database, returning summary metadata.
     */
    suspend fun inspectBackup(inputStream: InputStream, passphrase: String): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val json = decryptBackup(inputStream, passphrase).getOrThrow()
            val cycles = json.optJSONArray("cycles")?.length() ?: 0
            val logs = json.optJSONArray("daily_logs")?.length() ?: 0
            val symptoms = json.optJSONArray("symptoms")?.length() ?: 0
            val exportTime = json.optString("exported_at", "Unknown")

            BackupMetadata(
                exportTimestamp = exportTime,
                appIdentifier = json.optString("app", "Aura Cycle"),
                cyclesCount = cycles,
                logsCount = logs,
                symptomsCount = symptoms,
                earliestLogDate = null,
                latestLogDate = null
            )
        }
    }

    /**
     * Decrypts and restores all cycle data into the database within an atomic transaction.
     */
    suspend fun restoreDatabase(inputStream: InputStream, passphrase: String): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = decryptBackup(inputStream, passphrase).getOrThrow()
            val exportTime = json.optString("exported_at", "Unknown")

            // Parse cycles
            val cyclesArray = json.optJSONArray("cycles") ?: JSONArray()
            val restoredCycles = mutableListOf<CycleEntity>()
            for (i in 0 until cyclesArray.length()) {
                val obj = cyclesArray.getJSONObject(i)
                restoredCycles.add(
                    CycleEntity(
                        id = obj.optLong("id", 0L),
                        startDate = LocalDate.parse(obj.getString("startDate")),
                        endDate = if (obj.has("endDate") && !obj.isNull("endDate")) LocalDate.parse(obj.getString("endDate")) else null,
                        periodLengthDays = obj.optInt("periodLengthDays", 5),
                        cycleLengthDays = if (obj.has("cycleLengthDays") && !obj.isNull("cycleLengthDays")) obj.getInt("cycleLengthDays") else null
                    )
                )
            }

            // Parse daily logs
            val logsArray = json.optJSONArray("daily_logs") ?: JSONArray()
            val restoredLogs = mutableListOf<DailyLogEntity>()
            for (i in 0 until logsArray.length()) {
                val obj = logsArray.getJSONObject(i)
                val moodArray = obj.optJSONArray("mood") ?: JSONArray()
                val moods = mutableListOf<String>()
                for (m in 0 until moodArray.length()) {
                    moods.add(moodArray.getString(m))
                }

                restoredLogs.add(
                    DailyLogEntity(
                        date = LocalDate.parse(obj.getString("date")),
                        cycleId = if (obj.has("cycleId") && !obj.isNull("cycleId")) obj.getLong("cycleId") else null,
                        flowIntensity = obj.optString("flowIntensity").let { runCatching { FlowIntensity.valueOf(it) }.getOrDefault(FlowIntensity.NONE) },
                        mood = moods,
                        notes = obj.optString("notes", ""),
                        energyLevel = if (obj.has("energyLevel") && !obj.isNull("energyLevel")) runCatching { EnergyLevel.valueOf(obj.getString("energyLevel")) }.getOrNull() else null,
                        sleepQuality = if (obj.has("sleepQuality") && !obj.isNull("sleepQuality")) runCatching { SleepQuality.valueOf(obj.getString("sleepQuality")) }.getOrNull() else null,
                        bbtCelsius = if (obj.has("bbtCelsius") && !obj.isNull("bbtCelsius")) obj.getDouble("bbtCelsius") else null,
                        sexualActivity = if (obj.has("sexualActivity") && !obj.isNull("sexualActivity")) runCatching { SexualActivity.valueOf(obj.getString("sexualActivity")) }.getOrNull() else null,
                        medicationTaken = if (obj.has("medicationTaken") && !obj.isNull("medicationTaken")) obj.getBoolean("medicationTaken") else null,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse symptoms
            val symptomsArray = json.optJSONArray("symptoms") ?: JSONArray()
            val restoredSymptoms = mutableListOf<SymptomLogEntity>()
            for (i in 0 until symptomsArray.length()) {
                val obj = symptomsArray.getJSONObject(i)
                restoredSymptoms.add(
                    SymptomLogEntity(
                        id = 0L,
                        dailyLogId = LocalDate.parse(obj.getString("dailyLogId")),
                        symptomTag = obj.getString("symptomTag"),
                        severityLevel = obj.optInt("severityLevel", 2)
                    )
                )
            }

            // Parse user settings
            val settingsObj = json.optJSONObject("user_settings")
            val restoredSettings = settingsObj?.let { obj ->
                UserSettingsEntity(
                    id = 1,
                    lutealPhaseLengthDefault = obj.optInt("lutealPhaseLengthDefault", 14),
                    periodLengthDefault = obj.optInt("periodLengthDefault", 5),
                    cycleLengthDefault = obj.optInt("cycleLengthDefault", 28),
                    biometricEnabled = obj.optBoolean("biometricEnabled", false),
                    pinHash = if (obj.has("pinHash") && !obj.isNull("pinHash")) obj.getString("pinHash") else null,
                    notificationsEnabled = obj.optBoolean("notificationsEnabled", true),
                    fertileWindowAlertEnabled = obj.optBoolean("fertileWindowAlertEnabled", false),
                    dailyDigestAlertEnabled = obj.optBoolean("dailyDigestAlertEnabled", true),
                    phaseChangeAlertEnabled = obj.optBoolean("phaseChangeAlertEnabled", true),
                    accentColor = obj.optString("accentColor", "Brown")
                )
            }

            // Parse profile
            val profileObj = json.optJSONObject("profile")
            val restoredProfile = profileObj?.let { obj ->
                ProfileEntity(
                    id = 1,
                    name = obj.optString("name", ""),
                    onboardingCompletedAt = if (obj.has("onboardingCompletedAt") && !obj.isNull("onboardingCompletedAt")) obj.getLong("onboardingCompletedAt") else null
                )
            }

            // Execute atomic restoration within Room transaction
            database.withTransaction {
                // Clear existing records
                cycleDao.deleteAllCycles()
                dailyLogDao.deleteAllDailyLogs()
                symptomLogDao.deleteAllSymptoms()

                // Insert restored records
                for (cycle in restoredCycles) {
                    cycleDao.insertCycle(cycle)
                }
                for (log in restoredLogs) {
                    dailyLogDao.insertOrUpdate(log)
                }
                if (restoredSymptoms.isNotEmpty()) {
                    symptomLogDao.insertSymptoms(restoredSymptoms)
                }
                if (restoredSettings != null) {
                    userSettingsDao.insertOrUpdateSettings(restoredSettings)
                }
                if (restoredProfile != null) {
                    profileDao.insertOrUpdateProfile(restoredProfile)
                }
            }

            BackupRestoreResult(
                cyclesRestored = restoredCycles.size,
                logsRestored = restoredLogs.size,
                symptomsRestored = restoredSymptoms.size,
                exportTimestamp = exportTime
            )
        }
    }
}
