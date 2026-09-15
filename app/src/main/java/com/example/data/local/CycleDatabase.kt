package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.Converters
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

@Database(
    entities = [
        DailyLogEntity::class,
        SymptomLogEntity::class,
        CycleEntity::class,
        UserSettingsEntity::class,
        ProfileEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CycleDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun cycleDao(): CycleDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: CycleDatabase? = null

        private fun performSymptomMigration(db: SupportSQLiteDatabase) {
            // 1. Read existing symptoms from daily_logs before dropping column
            val migratedSymptoms = mutableListOf<Triple<String, String, Int>>()
            val existingColumns = mutableSetOf<String>()
            try {
                val pragma = db.query("PRAGMA table_info(daily_logs)")
                pragma.use {
                    val nameIndex = it.getColumnIndex("name")
                    if (nameIndex != -1) {
                        while (it.moveToNext()) {
                            existingColumns.add(it.getString(nameIndex))
                        }
                    }
                }
            } catch (_: Exception) {}

            if ("symptoms" in existingColumns) {
                try {
                    val cursor = db.query("SELECT date, symptoms FROM daily_logs WHERE symptoms IS NOT NULL AND symptoms != ''")
                    cursor.use {
                        val dateIndex = it.getColumnIndex("date")
                        val symptomsIndex = it.getColumnIndex("symptoms")
                        if (dateIndex != -1 && symptomsIndex != -1) {
                            while (it.moveToNext()) {
                                val date = it.getString(dateIndex)
                                val symptomsStr = it.getString(symptomsIndex)
                                if (!symptomsStr.isNullOrBlank()) {
                                    val list = symptomsStr.split(",")
                                    for (tag in list) {
                                        val trimmed = tag.trim()
                                        if (trimmed.isNotEmpty()) {
                                            migratedSymptoms.add(Triple(date, trimmed, 2)) // Moderate = 2
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Recreate daily_logs table without the symptoms column
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_logs_new` (
                    `date` TEXT NOT NULL,
                    `cycle_id` INTEGER,
                    `flow_intensity` TEXT NOT NULL,
                    `mood` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `energy_level` TEXT,
                    `sleep_quality` TEXT,
                    `bbt_celsius` REAL,
                    `sexual_activity` TEXT,
                    `medication_taken` INTEGER,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`date`)
                )
                """.trimIndent()
            )

            val cycleIdCol = if ("cycle_id" in existingColumns) "`cycle_id`" else "NULL"
            val flowCol = if ("flow_intensity" in existingColumns) "`flow_intensity`" else "'NONE'"
            val moodCol = if ("mood" in existingColumns) "`mood`" else "''"
            val notesCol = if ("notes" in existingColumns) "`notes`" else "''"
            val energyCol = if ("energy_level" in existingColumns) "`energy_level`" else "NULL"
            val sleepCol = if ("sleep_quality" in existingColumns) "`sleep_quality`" else "NULL"
            val bbtCol = if ("bbt_celsius" in existingColumns) "`bbt_celsius`" else "NULL"
            val sexCol = if ("sexual_activity" in existingColumns) "`sexual_activity`" else "NULL"
            val medCol = if ("medication_taken" in existingColumns) "`medication_taken`" else "NULL"
            val updatedCol = if ("updated_at" in existingColumns) "`updated_at`" else "(strftime('%s', 'now') * 1000)"

            try {
                db.execSQL(
                    """
                    INSERT INTO `daily_logs_new` (`date`, `cycle_id`, `flow_intensity`, `mood`, `notes`, `energy_level`, `sleep_quality`, `bbt_celsius`, `sexual_activity`, `medication_taken`, `updated_at`)
                    SELECT `date`, $cycleIdCol, $flowCol, $moodCol, $notesCol, $energyCol, $sleepCol, $bbtCol, $sexCol, $medCol, $updatedCol FROM `daily_logs`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `daily_logs`")
                db.execSQL("ALTER TABLE `daily_logs_new` RENAME TO `daily_logs`")
            } catch (_: Exception) {
                // If daily_logs didn't exist yet
            }

            // 3. Create symptom_logs child table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `symptom_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dailyLogId` TEXT NOT NULL,
                    `symptomTag` TEXT NOT NULL,
                    `severityLevel` INTEGER NOT NULL,
                    FOREIGN KEY(`dailyLogId`) REFERENCES `daily_logs`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_logs_dailyLogId` ON `symptom_logs` (`dailyLogId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_symptom_logs_dailyLogId_symptomTag` ON `symptom_logs` (`dailyLogId`, `symptomTag`)")

            // 4. Insert migrated symptoms with severity Moderate (2)
            for (item in migratedSymptoms) {
                val date = item.first
                val tag = item.second.replace("'", "''")
                val severity = item.third
                db.execSQL("INSERT OR REPLACE INTO `symptom_logs` (`dailyLogId`, `symptomTag`, `severityLevel`) VALUES ('$date', '$tag', $severity)")
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = performSymptomMigration(db)
        }
        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = performSymptomMigration(db)
        }
        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = performSymptomMigration(db)
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = performSymptomMigration(db)
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `accent_color` TEXT NOT NULL DEFAULT 'Brown'")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_settings` ADD COLUMN `phase_change_alert_enabled` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): CycleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CycleDatabase::class.java,
                    "cycle_encrypted.db"
                )
                    .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
