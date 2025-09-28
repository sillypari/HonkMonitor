package com.honkmonitor.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HonkRepository @Inject constructor(
    private val dao: HonkEventDao
) {
    
    fun getAllHonkEvents(): Flow<List<HonkEvent>> = dao.getAllHonkEvents()
    
    fun getTotalHonkCount(): Flow<Int> = dao.getTotalHonkCount()
    
    fun getHonkCountSince(since: Long): Flow<Int> = dao.getHonkCountSince(since)
    
    suspend fun insertHonkEvent(honkEvent: HonkEvent) {
        dao.insertHonkEvent(honkEvent)
    }
    
    suspend fun getEventsForExport(startTime: Long): List<HonkEvent> {
        return dao.getEventsForExport(startTime)
    }
    
    suspend fun cleanupOldEvents(cutoffTime: Long) {
        dao.deleteOldEvents(cutoffTime)
    }
}