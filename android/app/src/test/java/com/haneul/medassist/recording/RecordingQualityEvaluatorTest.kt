package com.haneul.medassist.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingQualityEvaluatorTest {
    @Test
    fun rejectsNinetySecondFileWithOnlyOpeningAudio() {
        assertFalse(
            RecordingQualityEvaluator.isUsable(
                durationMs = 90_000,
                audibleDurationMs = 5_000,
                trailingSilenceMs = 85_000,
                maxPeakAmplitude = 8_000,
                systemSilencedDurationMs = 0,
            ),
        )
    }

    @Test
    fun acceptsSpeechDistributedAcrossLongRecording() {
        assertTrue(
            RecordingQualityEvaluator.isUsable(
                durationMs = 90_000,
                audibleDurationMs = 25_000,
                trailingSilenceMs = 4_000,
                maxPeakAmplitude = 12_000,
                systemSilencedDurationMs = 0,
            ),
        )
    }

    @Test
    fun rejectsSystemSilencedInput() {
        assertFalse(
            RecordingQualityEvaluator.isUsable(
                durationMs = 30_000,
                audibleDurationMs = 12_000,
                trailingSilenceMs = 1_000,
                maxPeakAmplitude = 10_000,
                systemSilencedDurationMs = 3_000,
            ),
        )
    }

    @Test
    fun shortClipOnlyRequiresNonDigitalSilence() {
        assertTrue(RecordingQualityEvaluator.isUsable(2_000, 0, 2_000, 100, 0))
        assertFalse(RecordingQualityEvaluator.isUsable(2_000, 0, 2_000, 0, 0))
    }
}
