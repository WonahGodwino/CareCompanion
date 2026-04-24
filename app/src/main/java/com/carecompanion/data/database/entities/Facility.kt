package com.carecompanion.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facility")
data class Facility(
    @PrimaryKey val id: Long,
    val name: String,
    val facilityCode: String? = null,
    val state: String? = null,
    val lga: String? = null,
    val isActive: Boolean = false
)
