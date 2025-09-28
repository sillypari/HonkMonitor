package com.honkmonitor.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.honkmonitor.data.HonkEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Horn detection using Goertzel algorithm
 * Monitors audio input for horn-like frequency signatures
 */
class HornDetector(
    private val sampleRate: Int = 16000,
    private val frameSizeMs: Int = 200,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "HornDetector"
        private const val DEBOUNCE_MS = 700L
    }
    
    private val running = AtomicBoolean(false)
    private val bufferSize = (sampleRate * frameSizeMs / 1000)
    private var audioRecord: AudioRecord? = null
    private var lastHonkAt = 0L
    
    // Target frequencies for horn detection (300Hz - 2000Hz range)
    private val targetFreqs = listOf(300.0, 500.0, 800.0, 1200.0, 1500.0, 2000.0)
    
    private val goertzelFilters by lazy {
        targetFreqs.map { freq ->
            Goertzel(sampleRate, bufferSize, freq)
        }
    }
    
    fun start(onHonkDetected: (HonkEvent) -> Unit) {
        if (running.getAndSet(true)) {
            Log.w(TAG, "Horn detector already running")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                initializeAudioRecord()
                startDetection(onHonkDetected)
            } catch (e: Exception) {
                Log.e(TAG, "Error in horn detection", e)
            } finally {
                cleanup()
            }
        }
    }
    
    fun stop() {
        running.set(false)
    }
    
    private fun initializeAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            throw IllegalStateException("Failed to get minimum buffer size")
        }
        
        val actualBufferSize = max(minBufferSize, bufferSize * 2)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }
    }
    
    private fun startDetection(onHonkDetected: (HonkEvent) -> Unit) {
        val buffer = ShortArray(bufferSize)
        var noiseBaseline = 1e-6 // Adaptive noise floor
        
        audioRecord?.startRecording()
        Log.d(TAG, "Started audio recording for horn detection")
        
        while (running.get()) {
            val samplesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            
            if (samplesRead <= 0) {
                Thread.sleep(10) // Brief pause on read error
                continue
            }
            
            // Calculate RMS for noise floor adaptation
            var sumSquares = 0.0
            for (i in 0 until samplesRead) {
                sumSquares += buffer[i] * buffer[i]
            }
            val rms = sqrt(sumSquares / samplesRead)
            
            // Adaptive noise floor with slow adaptation
            noiseBaseline = (noiseBaseline * 0.98) + (rms * 0.02)
            
            // Calculate band energy using Goertzel filters
            var totalBandEnergy = 0.0
            for (filter in goertzelFilters) {
                totalBandEnergy += filter.process(buffer, samplesRead)
            }
            
            val normalizedBandEnergy = totalBandEnergy / samplesRead
            
            // Horn detection logic
            if (isHornDetected(normalizedBandEnergy, noiseBaseline)) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHonkAt > DEBOUNCE_MS) {
                    lastHonkAt = currentTime
                    
                    val confidence = calculateConfidence(normalizedBandEnergy, noiseBaseline)
                    val honkEvent = HonkEvent(
                        timestamp = currentTime,
                        latitude = 0.0, // Will be filled by the service
                        longitude = 0.0, // Will be filled by the service
                        confidence = confidence,
                        audioLevel = rms
                    )
                    
                    onHonkDetected(honkEvent)
                    Log.d(TAG, "Horn detected! Confidence: $confidence, Audio level: $rms")
                }
            }
        }
        
        audioRecord?.stop()
        Log.d(TAG, "Stopped audio recording")
    }
    
    private fun isHornDetected(bandEnergy: Double, noiseBaseline: Double): Boolean {
        // Horn detection threshold - tune this value based on testing
        val threshold = 6.0 * noiseBaseline
        return bandEnergy > threshold
    }
    
    private fun calculateConfidence(bandEnergy: Double, noiseBaseline: Double): Double {
        val ratio = if (noiseBaseline > 0) bandEnergy / noiseBaseline else 0.0
        return (ratio / 10.0).coerceIn(0.0, 1.0) // Normalize to 0-1 range
    }
    
    private fun cleanup() {
        audioRecord?.release()
        audioRecord = null
        running.set(false)
        Log.d(TAG, "Horn detector cleanup completed")
    }
}