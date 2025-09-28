package com.honkmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.honkmonitor.data.HonkDatabase
import com.honkmonitor.data.HonkRepository
import com.honkmonitor.databinding.ActivityMainBinding
import com.honkmonitor.service.MonitorForegroundService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: HonkRepository
    private var isServiceRunning = false
    
    // Required permissions
    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            updateUI()
        } else {
            Toast.makeText(this, "Permissions required for app to function", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Some permissions denied: ${permissions.filter { !it.value }.keys}")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize database and repository
        val database = HonkDatabase.getDatabase(this)
        repository = HonkRepository(database.honkEventDao())
        
        setupUI()
        checkPermissions()
        observeData()
    }
    
    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (hasAllPermissions()) {
                toggleService()
            } else {
                requestPermissions()
            }
        }
        
        binding.btnExport.setOnClickListener {
            exportData()
        }
        
        binding.btnClear.setOnClickListener {
            clearData()
        }
        
        updateUI()
    }
    
    private fun observeData() {
        lifecycleScope.launch {
            repository.getTotalHonkCount().collect { count ->
                binding.tvTotalHonks.text = "Total Honks: $count"
            }
        }
        
        lifecycleScope.launch {
            repository.getAllHonkEvents().collect { events ->
                // Update recent events display
                val recentEvents = events.take(5)
                val recentText = if (recentEvents.isEmpty()) {
                    "No honks detected yet"
                } else {
                    "Recent: " + recentEvents.joinToString(", ") { 
                        android.text.format.DateFormat.format("HH:mm:ss", it.timestamp).toString()
                    }
                }
                binding.tvRecentEvents.text = recentText
            }
        }
    }
    
    private fun toggleService() {
        if (isServiceRunning) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }
    
    private fun startMonitoring() {
        if (!hasAllPermissions()) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            return
        }
        
        MonitorForegroundService.startService(this)
        isServiceRunning = true
        updateUI()
        
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Started monitoring service")
    }
    
    private fun stopMonitoring() {
        MonitorForegroundService.stopService(this)
        isServiceRunning = false
        updateUI()
        
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Stopped monitoring service")
    }
    
    private fun updateUI() {
        val hasPermissions = hasAllPermissions()
        
        binding.btnStartStop.isEnabled = hasPermissions
        binding.btnStartStop.text = if (isServiceRunning) "Stop Monitoring" else "Start Monitoring"
        
        binding.tvStatus.text = when {
            !hasPermissions -> "❌ Permissions required"
            isServiceRunning -> "🟢 Monitoring active"
            else -> "⏸️ Ready to start"
        }
    }
    
    private fun exportData() {
        lifecycleScope.launch {
            try {
                val events = repository.getEventsForExport(0) // Export all events
                
                if (events.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create CSV content
                val csv = buildString {
                    appendLine("timestamp,latitude,longitude,confidence,audio_level")
                    events.forEach { event ->
                        appendLine("${event.timestamp},${event.latitude},${event.longitude},${event.confidence},${event.audioLevel}")
                    }
                }
                
                // TODO: Implement actual file saving (requires storage permissions)
                Log.d(TAG, "CSV data prepared: ${events.size} events")
                Toast.makeText(this@MainActivity, "Export prepared: ${events.size} events", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun clearData() {
        lifecycleScope.launch {
            try {
                repository.cleanupOldEvents(0) // Delete all events
                Toast.makeText(this@MainActivity, "Data cleared", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "All data cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Clear data failed", e)
                Toast.makeText(this@MainActivity, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissions() {
        if (!hasAllPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        val deniedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isNotEmpty()) {
            permissionLauncher.launch(deniedPermissions.toTypedArray())
        }
    }
}