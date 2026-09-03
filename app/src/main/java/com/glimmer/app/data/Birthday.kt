package com.glimmer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "birthdays")
data class Birthday(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dateOfBirth: Long,
    val relationship: String,
    val reminderEnabled: Boolean = true,
    val reminderTime: String = "1 day before",
    val photoUri: String? = null,
    val notes: String? = null,
    val phoneNumber: String? = null
)
