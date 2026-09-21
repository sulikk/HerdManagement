package com.example.herdmanagementmobile.data.local

import androidx.room.TypeConverter
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.IrzStatus
import com.example.herdmanagementmobile.domain.model.ListingStatus
import com.example.herdmanagementmobile.domain.model.MeasurementType
import com.example.herdmanagementmobile.domain.model.Sex
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localDateTimeToString(value: LocalDateTime?): String? =
        value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun stringToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter fun sexToString(value: Sex): String = value.name
    @TypeConverter fun stringToSex(value: String): Sex = Sex.valueOf(value)

    @TypeConverter fun eventTypeToString(value: EventType): String = value.name
    @TypeConverter fun stringToEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter fun irzStatusToString(value: IrzStatus): String = value.name
    @TypeConverter fun stringToIrzStatus(value: String): IrzStatus = IrzStatus.valueOf(value)

    @TypeConverter fun listingStatusToString(value: ListingStatus): String = value.name
    @TypeConverter fun stringToListingStatus(value: String): ListingStatus =
        ListingStatus.valueOf(value)

    @TypeConverter fun measurementTypeToString(value: MeasurementType): String = value.name
    @TypeConverter fun stringToMeasurementType(value: String): MeasurementType =
        MeasurementType.valueOf(value)
}
