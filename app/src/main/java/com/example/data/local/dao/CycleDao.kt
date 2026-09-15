package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles ORDER BY start_date DESC")
    fun getAllCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY start_date DESC")
    suspend fun getAllCyclesSync(): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE end_date IS NOT NULL ORDER BY start_date DESC")
    fun getCompletedCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE end_date IS NOT NULL ORDER BY start_date DESC")
    suspend fun getCompletedCyclesSync(): List<CycleEntity>

    @Query("SELECT * FROM cycles ORDER BY start_date DESC LIMIT 1")
    fun getLatestCycle(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles ORDER BY start_date DESC LIMIT 1")
    suspend fun getLatestCycleSync(): CycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: CycleEntity): Long

    @Update
    suspend fun updateCycle(cycle: CycleEntity)

    @Query("DELETE FROM cycles WHERE id = :id")
    suspend fun deleteCycle(id: Long)

    @Query("DELETE FROM cycles")
    suspend fun deleteAllCycles()
}
