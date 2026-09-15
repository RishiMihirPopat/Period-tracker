package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.data.model.EnergyLevel
import com.example.data.model.FlowIntensity
import com.example.data.model.SexualActivity
import com.example.data.model.SleepQuality
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromFlowIntensity(flow: FlowIntensity?): String? = flow?.name

    @TypeConverter
    fun toFlowIntensity(value: String?): FlowIntensity? =
        value?.let { runCatching { FlowIntensity.valueOf(it) }.getOrDefault(FlowIntensity.NONE) }

    @TypeConverter
    fun fromSexualActivity(activity: SexualActivity?): String? = activity?.name

    @TypeConverter
    fun toSexualActivity(value: String?): SexualActivity? =
        value?.let { runCatching { SexualActivity.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromEnergyLevel(energy: EnergyLevel?): String? = energy?.name

    @TypeConverter
    fun toEnergyLevel(value: String?): EnergyLevel? =
        value?.let { runCatching { EnergyLevel.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromSleepQuality(sleep: SleepQuality?): String? = sleep?.name

    @TypeConverter
    fun toSleepQuality(value: String?): SleepQuality? =
        value?.let { runCatching { SleepQuality.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.joinToString(separator = ",")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else value.split(",")
}
