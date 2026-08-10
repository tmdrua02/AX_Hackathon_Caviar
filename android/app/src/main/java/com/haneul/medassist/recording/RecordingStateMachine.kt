package com.haneul.medassist.recording

enum class RecordingPhase { IDLE, RECORDING, PAUSED, STOPPED, FAILED }
enum class RecordingEvent { START, PAUSE, RESUME, STOP, AUDIO_FOCUS_LOST, PHONE_CALL, ERROR }

object RecordingStateMachine {
    fun reduce(phase: RecordingPhase, event: RecordingEvent): RecordingPhase = when (phase to event) {
        RecordingPhase.IDLE to RecordingEvent.START -> RecordingPhase.RECORDING
        RecordingPhase.RECORDING to RecordingEvent.PAUSE,
        RecordingPhase.RECORDING to RecordingEvent.AUDIO_FOCUS_LOST,
        RecordingPhase.RECORDING to RecordingEvent.PHONE_CALL -> RecordingPhase.PAUSED
        RecordingPhase.PAUSED to RecordingEvent.RESUME -> RecordingPhase.RECORDING
        RecordingPhase.RECORDING to RecordingEvent.STOP,
        RecordingPhase.PAUSED to RecordingEvent.STOP -> RecordingPhase.STOPPED
        RecordingPhase.RECORDING to RecordingEvent.ERROR,
        RecordingPhase.PAUSED to RecordingEvent.ERROR -> RecordingPhase.FAILED
        else -> phase
    }
}

