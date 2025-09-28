package com.honkmonitor.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles GPS location tracking for the app
 */
class LocationTracker(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationTracker"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_UPDATE_INTERVAL = 2000L // 2 seconds
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTracking) {
            Log.w(TAG, "Location tracking already active")
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    _currentLocation.value = location
                    Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "Location availability: ${availability.isLocationAvailable}")
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        
        isTracking = true
        Log.d(TAG, "Started location tracking")
        
        // Get last known location immediately
        getLastKnownLocation()
    }
    
    fun stopLocationUpdates() {
        if (!isTracking) {
            Log.w(TAG, "Location tracking not active")
            return
        }
        
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        
        isTracking = false
        locationCallback = null
        Log.d(TAG, "Stopped location tracking")
    }
    
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                _currentLocation.value = it
                Log.d(TAG, "Got last known location: ${it.latitude}, ${it.longitude}")
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get last known location", exception)
        }
    }
    
    fun getCurrentLocationValue(): Location? = _currentLocation.value
}