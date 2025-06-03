package com.jamie.blinkchat.data.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val createdAt: String, // ISO 8601 timestamp
    val updatedAt: String  // ISO 8601 timestamp
)