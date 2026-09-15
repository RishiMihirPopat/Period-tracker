package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class DailyLogWithSymptoms(
    @Embedded
    val log: DailyLogEntity,

    @Relation(
        parentColumn = "date",
        entityColumn = "dailyLogId"
    )
    val symptoms: List<SymptomLogEntity> = emptyList()
)
