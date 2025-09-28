package com.honkmonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.honkmonitor.MainActivity
import com.honkmonitor.R
import com.honkmonitor.audio.HornDetector
import com.honkmonitor.data.HonkDatabase
import com.honkmonitor.data.HonkEvent
import com.honkmonitor.data.HonkRepository
import com.honkmonitor.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that monitors audio for horn events and tracks GPS location
 */
class MonitorForegroundService : Service() {
    
    companion object {
        private const val TAG = "MonitorService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "honk_monitor_channel"
        
        const val ACTION_START = "com.honkmonitor.ACTION_START"
        const val ACTION_STOP = "com.honkmonitor.ACTION_STOP"
        
        fun startService(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    
    private var hornDetector: HornDetector? = null
    private var locationTracker: LocationTracker? = null
    private var repository: HonkRepository? = null
    
    private var honkCount = 0
    private var startTime = 0L
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize components
        locationTracker = LocationTracker(this)
        hornDetector = HornDetector(scope = serviceScope)
        
        val database = HonkDatabase.getDatabase(this)
        repository = HonkRepository(database.honkEventDao())
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                if (isRunning.compareAndSet(false, true)) {
                    startMonitoring()
                }
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "Starting monitoring")
        startTime = System.currentTimeMillis()
        honkCount = 0
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start location tracking
        locationTracker?.startLocationUpdates()
        
        // Start horn detection
        hornDetector?.start { honkEvent ->
            handleHonkDetected(honkEvent)
        }
        
        Log.i(TAG, "Monitoring started successfully")
    }
    
    private fun stopMonitoring() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        
        Log.d(TAG, "Stopping monitoring")
        
        // Stop components
        hornDetector?.stop()
        locationTracker?.stopLocationUpdates()
        
        Log.i(TAG, "Monitoring stopped")
    }
    
    private fun handleHonkDetected(honkEvent: HonkEvent) {
        honkCount++
        Log.d(TAG, "Honk detected #$honkCount")
        
        // Get current location
        val location = locationTracker?.getCurrentLocationValue()
        val updatedEvent = honkEvent.copy(
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0
        )
        
        // Save to database
        serviceScope.launch {
            repository?.insertHonkEvent(updatedEvent)
        }
        
        // Update notification
        updateNotification()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Honk Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when honk monitoring is active"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, MonitorForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Honk Monitor Active")
            .setContentText(getNotificationText())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun getNotificationText(): String {
        val runningTime = (System.currentTimeMillis() - startTime) / 1000 / 60 // minutes
        return "Recording • $honkCount honks detected • ${runningTime}m"
    }
}