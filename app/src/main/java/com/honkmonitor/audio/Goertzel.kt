package com.honkmonitor.audio

import kotlin.math.cos
import kotlin.math.PI

/**
 * Goertzel algorithm implementation for frequency detection
 * Used to detect specific frequency components in audio signal
 */
class Goertzel(
    private val sampleRate: Int,
    private val blockSize: Int, 
    private val targetFreq: Double
) {
    private val k = (0.5 + (blockSize * targetFreq / sampleRate)).toInt()
    private val omega = (2.0 * PI * k) / blockSize
    private val coeff = 2.0 * cos(omega)
    
    /**
     * Process audio samples and return power at target frequency
     */
    fun process(samples: ShortArray, length: Int): Double {
        var sPrev = 0.0
        var sPrev2 = 0.0
        
        for (i in 0 until length) {
            val s = samples[i] + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }
        
        return sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2
    }
}