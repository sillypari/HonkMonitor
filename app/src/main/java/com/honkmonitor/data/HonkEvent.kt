package com.honkmonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "honk_events")
data class HonkEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val confidence: Double,
    val audioLevel: Double = 0.0
)