package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Child table storing individual logged symptoms with their severity level.
 * Severity level: 1 = Very Mild, 2 = Mild, 3 = Moderate, 4 = Severe, 5 = Very Severe.
 * Absence of a row means the symptom was not experienced on that day.
 */
@Entity(
    tableName = "symptom_logs",
    foreignKeys = [
        ForeignKey(
            entity = DailyLogEntity::class,
            parentColumns = ["date"],
            childColumns = ["dailyLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dailyLogId"]),
        Index(value = ["dailyLogId", "symptomTag"], unique = true)
    ]
)
data class SymptomLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "dailyLogId")
    val dailyLogId: LocalDate = LocalDate.now(),

    @ColumnInfo(name = "symptomTag")
    val symptomTag: String,

    @ColumnInfo(name = "severityLevel")
    val severityLevel: Int // 1 = Very Mild, 2 = Mild, 3 = Moderate, 4 = Severe, 5 = Very Severe
)
