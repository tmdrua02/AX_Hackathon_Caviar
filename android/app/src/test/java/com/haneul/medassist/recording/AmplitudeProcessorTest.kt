package com.haneul.medassist.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AmplitudeProcessorTest {
    @Test
    fun silentPcmProducesMinusSixtyDbAndMinimumHeight() {
        val result = processor().process(pcmAtDbfs(-60f), SAMPLE_COUNT, FRAME_MS)

        assertEquals(-60f, result.dbfs, 0.1f)
        assertEquals(0f, result.level, 0.001f)
        assertEquals(AmplitudeProcessor.MIN_BAR_HEIGHT, result.heightFraction, 0.001f)
    }

    @Test
    fun rmsUsesAllPcmSamples() {
        val samples = ShortArray(SAMPLE_COUNT) { if (it % 2 == 0) 16_384 else 0 }
        val result = processor().process(samples, samples.size, FRAME_MS)

        assertEquals(0.3535f, result.rms, 0.002f)
        assertEquals(-9.03f, result.dbfs, 0.1f)
    }

    @Test
    fun normalizationUsesMinusSixtyToZeroDbRange() {
        val result = processor(attackMs = 0.01f).process(pcmAtDbfs(-30f), SAMPLE_COUNT, FRAME_MS)

        assertEquals(0.5f, result.level, 0.01f)
    }

    @Test
    fun louderPcmProducesTallerBar() {
        val quiet = processor(attackMs = 0.01f).process(pcmAtDbfs(-45f), SAMPLE_COUNT, FRAME_MS)
        val loud = processor(attackMs = 0.01f).process(pcmAtDbfs(-15f), SAMPLE_COUNT, FRAME_MS)

        assertTrue(loud.heightFraction > quiet.heightFraction)
    }

    @Test
    fun dbRangesMapToRequestedBarHeights() {
        val expected = listOf(
            -56f to 0.05f,
            -40f to 0.25f,
            -25f to 0.60f,
            -12f to 0.85f,
            -3f to 1f,
        )

        expected.forEach { (dbfs, height) ->
            val result = processor(attackMs = 0.01f).process(pcmAtDbfs(dbfs), SAMPLE_COUNT, FRAME_MS)
            assertEquals(height, result.heightFraction, 0.02f)
        }
    }

    @Test
    fun valuesAlwaysStayInValidRanges() {
        val processor = processor()
        listOf(-60f, -50f, -30f, -10f, 0f).forEach { dbfs ->
            val result = processor.process(pcmAtDbfs(dbfs), SAMPLE_COUNT, FRAME_MS)
            assertTrue(result.level in 0f..1f)
            assertTrue(result.heightFraction in AmplitudeProcessor.MIN_BAR_HEIGHT..1f)
        }
    }

    @Test
    fun attackRespondsFasterThanRelease() {
        val processor = processor()
        val attacked = processor.process(pcmAtDbfs(0f), SAMPLE_COUNT, FRAME_MS).smoothedLevel
        val released = processor.process(pcmAtDbfs(-60f), SAMPLE_COUNT, FRAME_MS).smoothedLevel

        assertTrue(attacked > attacked - released)
    }

    @Test
    fun noiseFloorStaysAtMinimumHeight() {
        val processor = processor(attackMs = 0.01f)

        assertEquals(
            AmplitudeProcessor.MIN_BAR_HEIGHT,
            processor.process(pcmAtDbfs(-56f), SAMPLE_COUNT, FRAME_MS).heightFraction,
            0.001f,
        )
    }

    @Test
    fun resetClearsSmoothingState() {
        val processor = processor()
        processor.process(pcmAtDbfs(0f), SAMPLE_COUNT, FRAME_MS)

        processor.reset()

        assertEquals(0f, processor.process(pcmAtDbfs(-60f), SAMPLE_COUNT, FRAME_MS).smoothedLevel, 0.001f)
    }

    @Test
    fun minusThreeDbAndAboveIsClipped() {
        val result = processor().process(pcmAtDbfs(-2f), SAMPLE_COUNT, FRAME_MS)

        assertTrue(result.clipped)
    }

    @Test
    fun consecutiveSaturatedPcmIsDetected() {
        val processor = processor(saturationFrames = 3)
        val saturated = ShortArray(SAMPLE_COUNT) { Short.MAX_VALUE }
        assertFalse(processor.process(saturated, saturated.size, FRAME_MS).saturationDetected)
        assertFalse(processor.process(saturated, saturated.size, FRAME_MS).saturationDetected)

        assertTrue(processor.process(saturated, saturated.size, FRAME_MS).saturationDetected)
    }

    private fun processor(
        attackMs: Float = AmplitudeProcessor.ATTACK_TIME_MS,
        releaseMs: Float = AmplitudeProcessor.RELEASE_TIME_MS,
        saturationFrames: Int = AmplitudeProcessor.SATURATION_FRAME_COUNT,
    ) = AmplitudeProcessor(
        attackTimeMs = attackMs,
        releaseTimeMs = releaseMs,
        saturationFrameCount = saturationFrames,
    )

    private fun pcmAtDbfs(dbfs: Float): ShortArray {
        if (dbfs <= -60f) return ShortArray(SAMPLE_COUNT)
        val amplitude = (32_767.0 * 10.0.pow(dbfs / 20.0)).toInt().coerceIn(0, 32_767).toShort()
        return ShortArray(SAMPLE_COUNT) { amplitude }
    }

    companion object {
        private const val SAMPLE_COUNT = 1_470
        private const val FRAME_MS = 1_000f / 30f
    }
}
