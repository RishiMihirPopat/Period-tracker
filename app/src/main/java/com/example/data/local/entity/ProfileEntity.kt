package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "onboarding_completed_at")
    val onboardingCompletedAt: Long? = null
)
