package com.haneul.medassist.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingStateMachineTest {
    @Test
    fun pauseResumeStopSequence() {
        var phase = RecordingStateMachine.reduce(RecordingPhase.IDLE, RecordingEvent.START)
        phase = RecordingStateMachine.reduce(phase, RecordingEvent.PAUSE)
        assertEquals(RecordingPhase.PAUSED, phase)
        phase = RecordingStateMachine.reduce(phase, RecordingEvent.RESUME)
        assertEquals(RecordingPhase.RECORDING, phase)
        phase = RecordingStateMachine.reduce(phase, RecordingEvent.STOP)
        assertEquals(RecordingPhase.STOPPED, phase)
    }

    @Test
    fun phoneCallPausesInsteadOfDiscardingRecording() {
        val phase = RecordingStateMachine.reduce(RecordingPhase.RECORDING, RecordingEvent.PHONE_CALL)
        assertEquals(RecordingPhase.PAUSED, phase)
    }
}

