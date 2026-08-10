package com.haneul.medassist.recording

/** Pure recording-quality rules, kept separate so long-recording regressions can be unit tested. */
object RecordingQualityEvaluator {
    fun isUsable(
        durationMs: Long,
        audibleDurationMs: Long,
        trailingSilenceMs: Long,
        maxPeakAmplitude: Int,
        systemSilencedDurationMs: Long,
    ): Boolean {
        if (systemSilencedDurationMs >= SYSTEM_SILENCED_REJECTION_MS) return false
        if (durationMs < MIN_RECORDING_DURATION_MS) return false
        val requiredAudibleMs = minOf(MIN_AUDIBLE_DURATION_MS, durationMs / MIN_AUDIBLE_RATIO_DIVISOR)
        val excessiveTrailingSilence = durationMs >= TRAILING_SILENCE_CHECK_MIN_DURATION_MS &&
            trailingSilenceMs >= TRAILING_SILENCE_MIN_DURATION_MS &&
            trailingSilenceMs * 4 >= durationMs * 3
        return audibleDurationMs >= requiredAudibleMs && !excessiveTrailingSilence
    }

    private const val MIN_RECORDING_DURATION_MS = 3_000L
    private const val MIN_AUDIBLE_DURATION_MS = 2_000L
    private const val MIN_AUDIBLE_RATIO_DIVISOR = 20L
    private const val TRAILING_SILENCE_CHECK_MIN_DURATION_MS = 20_000L
    private const val TRAILING_SILENCE_MIN_DURATION_MS = 15_000L
    private const val SYSTEM_SILENCED_REJECTION_MS = 2_000L
}
