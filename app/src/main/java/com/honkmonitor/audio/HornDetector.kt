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
    
    // Target frequencies for Indian vehicle horns (more specific ranges)
    private val hornFreqRanges = listOf(
        // Primary horn frequencies (most Indian vehicles)
        FreqRange(400.0, 450.0, 1.0),   // Low car horns
        FreqRange(480.0, 520.0, 1.2),   // Standard car horns  
        FreqRange(600.0, 650.0, 1.1),   // Compact car horns
        FreqRange(800.0, 900.0, 1.3),   // Motorcycle/scooter horns
        FreqRange(1000.0, 1200.0, 1.1), // Higher pitch cars
        FreqRange(1400.0, 1600.0, 0.8), // Trucks/buses (lower priority)
        // Avoid music frequencies
        FreqRange(250.0, 350.0, 0.3),   // Too low (music bass)
        FreqRange(2000.0, 4000.0, 0.2)  // Too high (music treble)
    )
    
    private val goertzelFilters by lazy {
        hornFreqRanges.map { range ->
            HornFreqFilter(
                Goertzel(sampleRate, bufferSize, range.centerFreq),
                range.weight,
                range.minFreq,
                range.maxFreq
            )
        }
    }
    
    data class FreqRange(val minFreq: Double, val maxFreq: Double, val weight: Double) {
        val centerFreq = (minFreq + maxFreq) / 2.0
    }
    
    data class HornFreqFilter(
        val goertzel: Goertzel,
        val weight: Double,
        val minFreq: Double,
        val maxFreq: Double
    )
    
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
    
    @Suppress("MissingPermission")
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
            
            // Calculate weighted band energy using Indian horn frequency analysis
            var totalHornScore = 0.0
            var totalMusicScore = 0.0
            
            for (filter in goertzelFilters) {
                val energy = filter.goertzel.process(buffer, samplesRead)
                val normalizedEnergy = energy / samplesRead
                
                if (filter.weight > 0.5) {
                    // Horn frequency ranges (weighted positive)
                    totalHornScore += normalizedEnergy * filter.weight
                } else {
                    // Music frequency ranges (weighted negative)
                    totalMusicScore += normalizedEnergy * filter.weight
                }
            }
            
            // Advanced horn detection logic
            val hornToMusicRatio = if (totalMusicScore > 0) totalHornScore / totalMusicScore else totalHornScore
            val adaptiveThreshold = calculateAdaptiveThreshold(noiseBaseline, rms)
            
            if (isIndianHornPattern(totalHornScore, hornToMusicRatio, adaptiveThreshold, rms)) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHonkAt > DEBOUNCE_MS) {
                    lastHonkAt = currentTime
                    
                    val confidence = calculateConfidence(totalHornScore, totalMusicScore, noiseBaseline)
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
    
    private fun calculateAdaptiveThreshold(noiseBaseline: Double, rms: Double): Double {
        // Dynamic threshold based on current noise levels and signal strength
        val baseThreshold = 6.0 * noiseBaseline
        val signalAdjustment = rms * 0.1 // Adjust based on current signal level
        return maxOf(baseThreshold, signalAdjustment)
    }
    
    private fun isIndianHornPattern(hornScore: Double, hornToMusicRatio: Double, threshold: Double, rms: Double): Boolean {
        // Check if signal strength exceeds adaptive threshold
        if (hornScore < threshold) return false
        
        // Indian horns typically have strong presence in 400-1600Hz range
        // and show dominance over music frequencies
        val hasStrongHornSignal = hornScore > (rms * 0.3) // Horn signal is significant portion of total
        val dominatesMusic = hornToMusicRatio > 2.0 // Horn frequencies dominate music frequencies
        val adequateSignalStrength = rms > 0.02 // Minimum signal strength to avoid noise
        
        return hasStrongHornSignal && dominatesMusic && adequateSignalStrength
    }
    
    private fun calculateConfidence(hornScore: Double, musicScore: Double, noiseBaseline: Double): Double {
        // Calculate horn-to-music ratio
        val hornToMusicRatio = if (musicScore > 0.001) hornScore / musicScore else hornScore / 0.001
        
        // Calculate signal-to-noise ratio for overall energy
        val totalEnergy = hornScore + musicScore
        val snr = totalEnergy / maxOf(noiseBaseline, 0.001)
        
        // Confidence based on horn dominance and signal strength
        val hornDominance = hornScore / maxOf(totalEnergy, 0.001)
        val baseConfidence = minOf(1.0, snr / 10.0)
        
        // Boost confidence if horn frequencies dominate over music frequencies
        return if (hornToMusicRatio > 1.5 && hornDominance > 0.6) {
            minOf(1.0, baseConfidence * 1.5)
        } else {
            baseConfidence * hornDominance
        }
    }
    
    private fun cleanup() {
        audioRecord?.release()
        audioRecord = null
        running.set(false)
        Log.d(TAG, "Horn detector cleanup completed")
    }
}