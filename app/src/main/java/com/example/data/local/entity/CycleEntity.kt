package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate? = null,

    @ColumnInfo(name = "period_length_days")
    val periodLengthDays: Int = 5,

    @ColumnInfo(name = "cycle_length_days")
    val cycleLengthDays: Int? = null
)
