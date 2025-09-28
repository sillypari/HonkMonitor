package com.honkmonitor.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [HonkEvent::class],
    version = 1,
    exportSchema = false
)
abstract class HonkDatabase : RoomDatabase() {
    
    abstract fun honkEventDao(): HonkEventDao
    
    companion object {
        @Volatile
        private var INSTANCE: HonkDatabase? = null
        
        fun getDatabase(context: Context): HonkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HonkDatabase::class.java,
                    "honk_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}