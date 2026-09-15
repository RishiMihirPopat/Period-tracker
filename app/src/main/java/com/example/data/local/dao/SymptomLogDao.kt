package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SymptomLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SymptomLogDao {
    @Query("SELECT * FROM symptom_logs WHERE dailyLogId = :date")
    fun getSymptomsForDate(date: LocalDate): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs WHERE dailyLogId = :date")
    suspend fun getSymptomsForDateSync(date: LocalDate): List<SymptomLogEntity>

    @Query("SELECT * FROM symptom_logs")
    fun getAllSymptoms(): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs")
    suspend fun getAllSymptomsSync(): List<SymptomLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptoms(symptoms: List<SymptomLogEntity>)

    @Query("DELETE FROM symptom_logs WHERE dailyLogId = :date")
    suspend fun deleteSymptomsForDate(date: LocalDate)

    @Query("DELETE FROM symptom_logs WHERE dailyLogId = :date AND symptomTag = :tag")
    suspend fun deleteSymptom(date: LocalDate, tag: String)

    @Query("DELETE FROM symptom_logs")
    suspend fun deleteAllSymptoms()
}
