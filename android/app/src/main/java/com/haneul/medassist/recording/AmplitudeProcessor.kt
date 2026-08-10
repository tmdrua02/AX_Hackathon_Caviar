package com.haneul.medassist.recording

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

data class WaveformBar(
    val heightFraction: Float = AmplitudeProcessor.MIN_BAR_HEIGHT,
    val clipped: Boolean = false,
)

/** Converts PCM 16-bit microphone samples into display-ready RMS/dBFS values. */
class AmplitudeProcessor(
    private val attackTimeMs: Float = ATTACK_TIME_MS,
    private val releaseTimeMs: Float = RELEASE_TIME_MS,
    private val noiseFloorDbfs: Float = NOISE_FLOOR_DBFS,
    private val saturationFrameCount: Int = SATURATION_FRAME_COUNT,
) {
    data class Result(
        val rms: Float,
        val dbfs: Float,
        val level: Float,
        val smoothedLevel: Float,
        val heightFraction: Float,
        val peakAmplitude: Int,
        val clipped: Boolean,
        val saturationDetected: Boolean,
    )

    private var smoothedLevel = 0f
    private var consecutiveSaturatedFrames = 0

    fun process(samples: ShortArray, sampleCount: Int, frameDurationMs: Float): Result {
        val count = sampleCount.coerceIn(0, samples.size)
        var sumOfSquares = 0.0
        var peakAmplitude = 0
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            sumOfSquares += sample.toDouble() * sample.toDouble()
            peakAmplitude = maxOf(peakAmplitude, abs(sample))
        }

        val rms = if (count == 0) 0f else (sqrt(sumOfSquares / count) / PCM_FULL_SCALE).toFloat()
        val dbfs = if (rms <= 0f) {
            MIN_DBFS
        } else {
            (20f * log10(rms)).coerceIn(MIN_DBFS, MAX_DBFS)
        }
        val normalizedLevel = ((dbfs + 60f) / 60f).coerceIn(0f, 1f)
        val targetLevel = if (dbfs <= noiseFloorDbfs) 0f else normalizedLevel
        val timeConstantMs = if (targetLevel > smoothedLevel) attackTimeMs else releaseTimeMs
        val elapsedMs = frameDurationMs.coerceAtLeast(1f)
        val smoothing = (1f - exp(-elapsedMs / timeConstantMs)).coerceIn(0f, 1f)
        smoothedLevel = (smoothedLevel + (targetLevel - smoothedLevel) * smoothing).coerceIn(0f, 1f)

        consecutiveSaturatedFrames = if (peakAmplitude >= SATURATION_PEAK_AMPLITUDE) {
            consecutiveSaturatedFrames + 1
        } else {
            0
        }

        val smoothedDbfs = smoothedLevel * 60f - 60f
        return Result(
            rms = rms,
            dbfs = dbfs,
            level = normalizedLevel,
            smoothedLevel = smoothedLevel,
            heightFraction = heightForDbfs(smoothedDbfs),
            peakAmplitude = peakAmplitude,
            clipped = dbfs >= CLIPPING_DBFS,
            saturationDetected = consecutiveSaturatedFrames == saturationFrameCount,
        )
    }

    fun reset() {
        smoothedLevel = 0f
        consecutiveSaturatedFrames = 0
    }

    private fun heightForDbfs(dbfs: Float): Float = when {
        dbfs <= NOISE_FLOOR_DBFS -> MIN_BAR_HEIGHT
        dbfs < -40f -> lerp(MIN_BAR_HEIGHT, 0.25f, (dbfs - NOISE_FLOOR_DBFS) / 15f)
        dbfs < -25f -> lerp(0.25f, 0.60f, (dbfs + 40f) / 15f)
        dbfs < -12f -> lerp(0.60f, 0.85f, (dbfs + 25f) / 13f)
        dbfs < CLIPPING_DBFS -> lerp(0.85f, 1f, (dbfs + 12f) / 9f)
        else -> 1f
    }.coerceIn(MIN_BAR_HEIGHT, 1f)

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction.coerceIn(0f, 1f)

    companion object {
        const val MIN_DBFS = -60f
        const val MAX_DBFS = 0f
        const val NOISE_FLOOR_DBFS = -55f
        const val CLIPPING_DBFS = -3f
        const val MIN_BAR_HEIGHT = 0.05f

        const val ATTACK_TIME_MS = 40f
        const val RELEASE_TIME_MS = 220f

        const val SATURATION_PEAK_AMPLITUDE = 32_760
        const val SATURATION_FRAME_COUNT = 5
        private const val PCM_FULL_SCALE = 32_768.0
    }
}
