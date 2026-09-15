package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import java.time.LocalDate

@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey
    val date: LocalDate,

    @ColumnInfo(name = "cycle_id")
    val cycleId: Long? = null,

    @ColumnInfo(name = "flow_intensity")
    val flowIntensity: FlowIntensity = FlowIntensity.NONE,

    val mood: List<String> = emptyList(),

    val notes: String = "",

    @ColumnInfo(name = "energy_level")
    val energyLevel: EnergyLevel? = null,

    @ColumnInfo(name = "sleep_quality")
    val sleepQuality: SleepQuality? = null,

    @ColumnInfo(name = "bbt_celsius")
    val bbtCelsius: Double? = null,

    @ColumnInfo(name = "sexual_activity")
    val sexualActivity: SexualActivity? = null,

    @ColumnInfo(name = "medication_taken")
    val medicationTaken: Boolean? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
