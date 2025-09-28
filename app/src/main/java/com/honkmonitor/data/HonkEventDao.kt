package com.honkmonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HonkEventDao {
    
    @Query("SELECT * FROM honk_events ORDER BY timestamp DESC")
    fun getAllHonkEvents(): Flow<List<HonkEvent>>
    
    @Query("SELECT * FROM honk_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getHonkEventsBetween(startTime: Long, endTime: Long): Flow<List<HonkEvent>>
    
    @Query("SELECT COUNT(*) FROM honk_events")
    fun getTotalHonkCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM honk_events WHERE timestamp >= :since")
    fun getHonkCountSince(since: Long): Flow<Int>
    
    @Insert
    suspend fun insertHonkEvent(honkEvent: HonkEvent)
    
    @Query("DELETE FROM honk_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEvents(cutoffTime: Long)
    
    @Query("SELECT * FROM honk_events WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getEventsForExport(startTime: Long): List<HonkEvent>
}