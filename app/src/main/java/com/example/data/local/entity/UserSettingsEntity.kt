package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "luteal_phase_length_default")
    val lutealPhaseLengthDefault: Int = 14,

    @ColumnInfo(name = "period_length_default")
    val periodLengthDefault: Int = 5,

    @ColumnInfo(name = "cycle_length_default")
    val cycleLengthDefault: Int = 28,

    @ColumnInfo(name = "biometric_enabled")
    val biometricEnabled: Boolean = false,

    @ColumnInfo(name = "pin_hash")
    val pinHash: String? = null,

    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,

    @ColumnInfo(name = "fertile_window_alert_enabled")
    val fertileWindowAlertEnabled: Boolean = false,

    @ColumnInfo(name = "daily_digest_alert_enabled")
    val dailyDigestAlertEnabled: Boolean = true,

    @ColumnInfo(name = "phase_change_alert_enabled", defaultValue = "1")
    val phaseChangeAlertEnabled: Boolean = true,

    @ColumnInfo(name = "accent_color", defaultValue = "Brown")
    val accentColor: String = "Brown"
)
