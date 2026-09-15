package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.DailyLogEntity
import com.example.data.local.entity.DailyLogWithSymptoms
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    fun getLogForDate(date: LocalDate): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getLogForDateSync(date: LocalDate): DailyLogEntity?

    @Transaction
    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    fun getLogWithSymptoms(date: LocalDate): Flow<DailyLogWithSymptoms?>

    @Transaction
    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getLogWithSymptomsSync(date: LocalDate): DailyLogWithSymptoms?

    @Query("SELECT * FROM daily_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getLogsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getLogsBetweenSync(startDate: LocalDate, endDate: LocalDate): List<DailyLogEntity>

    @Transaction
    @Query("SELECT * FROM daily_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getLogsWithSymptomsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLogWithSymptoms>>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllDailyLogs(): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllDailyLogsSync(): List<DailyLogEntity>

    @Transaction
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllLogsWithSymptoms(): Flow<List<DailyLogWithSymptoms>>

    @Transaction
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllLogsWithSymptomsSync(): List<DailyLogWithSymptoms>

    @Query("SELECT * FROM daily_logs WHERE flow_intensity != 'NONE' ORDER BY date DESC")
    fun getAllFlowLogs(): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE flow_intensity != 'NONE' ORDER BY date DESC")
    suspend fun getAllFlowLogsSync(): List<DailyLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyLogEntity)

    @Query("DELETE FROM daily_logs WHERE date = :date")
    suspend fun deleteLogForDate(date: LocalDate)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAllDailyLogs()
}
