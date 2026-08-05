package com.haneul.medassist

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haneul.medassist.data.*
import com.haneul.medassist.ocr.OcrEngine
import com.haneul.medassist.recording.AmplitudeProcessor
import com.haneul.medassist.recording.PcmAacRecorder
import com.haneul.medassist.recording.WaveformBar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RecordingUiState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val finalizing: Boolean = false,
    val readyToSave: Boolean = false,
    val elapsedMs: Long = 0,
    val stoppedFile: File? = null,
    val error: String? = null,
)

data class AppUiState(
    val home: LoadState<HomeResponse> = LoadState.Idle,
    val medications: List<Medication> = emptyList(),
    val selectedExisting: Set<String> = emptySet(),
    val frontPhoto: Uri? = null,
    val backPhoto: Uri? = null,
    val draft: PrescriptionDraft? = null,
    val newMedication: Medication? = null,
    val draftLoading: Boolean = false,
    val interactionAccepted: Accepted? = null,
    val interaction: LoadState<InteractionCheck> = LoadState.Idle,
    val consultations: LoadState<List<Consultation>> = LoadState.Idle,
    val recording: RecordingUiState = RecordingUiState(),
    val chatMessages: List<Pair<Boolean, String>> = emptyList(),
    val chatLoading: Boolean = false,
    val snackbar: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: MedAssistRepository,
    private val ocr: OcrEngine,
    private val savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _waveform = MutableStateFlow(emptyWaveform())
    val waveform: StateFlow<List<WaveformBar>> = _waveform.asStateFlow()
    private var recorder: PcmAacRecorder? = null
    private val amplitudeProcessor = AmplitudeProcessor()
    private var lastElapsedUiUpdateMs = 0L
    private var lastAmplitudeLogAtMs = 0L

    init {
        refreshHome()
        loadMedications()
        loadConsultations()
    }

    fun refreshHome() = viewModelScope.launch {
        _state.update { it.copy(home = LoadState.Loading) }
        _state.update { it.copy(home = repository.home()) }
    }

    private fun loadMedications() = viewModelScope.launch {
        val medications = repository.medications()
        _state.update { current ->
            current.copy(
                medications = medications,
                selectedExisting = medications.filter { it.active }.map { it.id }.toSet(),
            )
        }
    }

    fun toggleDose(medication: Medication) = viewModelScope.launch {
        val currentHome = (_state.value.home as? LoadState.Content)?.value ?: return@launch
        val optimistic = currentHome.copy(todayMedications = currentHome.todayMedications.map {
            if (it.id == medication.id) it.copy(taken = !medication.taken, version = medication.version + 1) else it
        })
        _state.update { it.copy(home = LoadState.Content(optimistic)) }
        repository.setDose(medication, !medication.taken).onFailure {
            _state.update { state -> state.copy(home = LoadState.Content(currentHome), snackbar = "동기화에 실패해 변경을 되돌렸습니다.") }
        }
    }

    fun toggleExisting(id: String) = _state.update { state ->
        val selected = state.selectedExisting.toMutableSet().apply { if (!add(id)) remove(id) }
        state.copy(selectedExisting = selected)
    }

    fun setPhoto(front: Boolean, uri: Uri) = _state.update {
        if (front) it.copy(frontPhoto = uri) else it.copy(backPhoto = uri)
    }

    fun clearPhoto(front: Boolean) = _state.update {
        if (front) it.copy(frontPhoto = null) else it.copy(backPhoto = null)
    }

    fun submitPhotos(onReady: () -> Unit) = viewModelScope.launch {
        val front = _state.value.frontPhoto ?: return@launch
        val back = _state.value.backPhoto ?: return@launch
        _state.update { it.copy(draftLoading = true) }
        val ocrText = runCatching { ocr.recognize(listOf(front, back)) }.getOrDefault("")
        val draft = repository.createDraft(front, back, context.contentResolver, ocrText)
        _state.update { it.copy(draft = draft, draftLoading = false) }
        onReady()
    }

    fun updateDraft(draft: PrescriptionDraft) = _state.update { it.copy(draft = draft) }

    fun confirmDraft(onConfirmed: () -> Unit) = viewModelScope.launch {
        val draft = _state.value.draft ?: return@launch
        _state.update { it.copy(draftLoading = true) }
        val medication = repository.confirmDraft(draft)
        _state.update { it.copy(newMedication = medication, draftLoading = false) }
        onConfirmed()
    }

    fun startAnalysis(onStarted: () -> Unit) = viewModelScope.launch {
        val added = _state.value.newMedication ?: return@launch
        val existing = _state.value.medications.filter { it.id in _state.value.selectedExisting }
        if (existing.isEmpty()) return@launch
        _state.update { it.copy(interaction = LoadState.Loading) }
        val accepted = repository.createCheck(added, existing)
        savedState["checkId"] = accepted.resourceId
        savedState["jobId"] = accepted.jobId
        _state.update { it.copy(interactionAccepted = accepted) }
        onStarted()
    }

    fun finishAnalysis(onSuccess: () -> Unit) = viewModelScope.launch {
        delay(1_200)
        val accepted = _state.value.interactionAccepted ?: return@launch
        val added = _state.value.newMedication ?: return@launch
        val existing = _state.value.medications.filter { it.id in _state.value.selectedExisting }
        runCatching { repository.check(accepted, added, existing) }
            .onSuccess {
                _state.update { state -> state.copy(interaction = LoadState.Content(it)) }
                onSuccess()
            }
            .onFailure {
                _state.update { state -> state.copy(interaction = LoadState.Error("분석 상태를 불러오지 못했습니다.")) }
            }
    }

    fun saveInteraction() = viewModelScope.launch {
        val check = (_state.value.interaction as? LoadState.Content)?.value ?: return@launch
        val saved = repository.saveCheck(check)
        _state.update { it.copy(interaction = LoadState.Content(saved), snackbar = "결과를 기록에 저장했습니다.") }
    }

    fun loadConsultations() = viewModelScope.launch {
        _state.update { it.copy(consultations = LoadState.Loading) }
        val values = repository.consultations()
        _state.update { it.copy(consultations = if (values.isEmpty()) LoadState.Empty else LoadState.Content(values)) }
    }

    fun startRecording(): Boolean {
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(directory, "consultation-${System.currentTimeMillis()}.m4a")
        amplitudeProcessor.reset()
        lastElapsedUiUpdateMs = 0L
        lastAmplitudeLogAtMs = 0L
        _waveform.value = emptyWaveform()
        _state.update {
            it.copy(recording = RecordingUiState(active = true, stoppedFile = file))
        }

        return try {
            val newRecorder = PcmAacRecorder(file, object : PcmAacRecorder.Listener {
                override fun onPcmFrame(
                    samples: ShortArray,
                    sampleCount: Int,
                    frameDurationMs: Float,
                    recordedDurationMs: Long,
                ) {
                    val amplitude = amplitudeProcessor.process(samples, sampleCount, frameDurationMs)
                    _waveform.update { values ->
                        ArrayList<WaveformBar>(WAVEFORM_SAMPLE_COUNT).apply {
                            for (index in 1 until values.size) add(values[index])
                            add(WaveformBar(amplitude.heightFraction, amplitude.clipped))
                        }
                    }

                    if (recordedDurationMs - lastElapsedUiUpdateMs >= ELAPSED_UI_INTERVAL_MS) {
                        lastElapsedUiUpdateMs = recordedDurationMs
                        _state.update { state ->
                            state.copy(recording = state.recording.copy(elapsedMs = recordedDurationMs))
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAmplitudeLogAtMs >= AMPLITUDE_LOG_INTERVAL_MS) {
                            lastAmplitudeLogAtMs = now
                            Log.d(
                                AMPLITUDE_LOG_TAG,
                                "peak=${amplitude.peakAmplitude}, rms=${amplitude.rms}, dBFS=${amplitude.dbfs}, level=${amplitude.level}, smoothed=${amplitude.smoothedLevel}",
                            )
                        }
                        if (amplitude.saturationDetected) {
                            Log.w(AMPLITUDE_LOG_TAG, "emulator/host microphone input may be clipped")
                        }
                    }
                }

                override fun onRecordingError(message: String, cause: Throwable?) {
                    if (BuildConfig.DEBUG) Log.e(AMPLITUDE_LOG_TAG, message, cause)
                    _state.update { state ->
                        state.copy(recording = state.recording.copy(active = false, error = message))
                    }
                }

                override fun onRecordingFinalized(recordedDurationMs: Long, error: Throwable?) {
                    recorder = null
                    _state.update { state ->
                        state.copy(recording = state.recording.copy(
                            active = false,
                            paused = false,
                            finalizing = false,
                            readyToSave = error == null && recordedDurationMs > 0L,
                            elapsedMs = recordedDurationMs,
                            error = state.recording.error ?: error?.let { "녹음 파일을 마무리할 수 없습니다: ${it.localizedMessage}" },
                        ))
                    }
                }
            })
            recorder = newRecorder
            newRecorder.start()
            true
        } catch (error: Exception) {
            recorder = null
            _state.update { it.copy(recording = RecordingUiState(error = "녹음을 시작할 수 없습니다: ${error.localizedMessage}")) }
            false
        }
    }

    fun pauseResumeRecording() {
        val current = _state.value.recording
        runCatching {
            val activeRecorder = checkNotNull(recorder) { "Recorder is unavailable" }
            if (current.paused) activeRecorder.resume() else activeRecorder.pause()
        }.onSuccess {
            _state.update { state ->
                state.copy(recording = state.recording.copy(paused = !current.paused))
            }
        }.onFailure { error ->
            _state.update { state ->
                state.copy(recording = state.recording.copy(error = "녹음 상태를 변경할 수 없습니다: ${error.localizedMessage}"))
            }
        }
    }

    fun stopRecording() {
        val activeRecorder = recorder
        if (activeRecorder == null) {
            _state.update { state ->
                state.copy(recording = state.recording.copy(
                    active = false,
                    paused = false,
                    finalizing = false,
                    error = state.recording.error ?: "활성 녹음 장치를 찾을 수 없습니다.",
                ))
            }
            return
        }
        _state.update { state ->
            state.copy(recording = state.recording.copy(active = false, paused = false, finalizing = true))
        }
        runCatching { activeRecorder.stop() }.onFailure { error ->
            _state.update { state ->
                state.copy(recording = state.recording.copy(
                    finalizing = false,
                    error = "녹음을 종료할 수 없습니다: ${error.localizedMessage}",
                ))
            }
        }
    }

    fun stopRecordingIfActive() {
        if (_state.value.recording.active) stopRecording()
    }

    fun recordingPermissionDenied() {
        _state.update { state ->
            state.copy(recording = state.recording.copy(error = "마이크 권한이 거부되어 녹음을 시작할 수 없습니다."))
        }
    }

    fun saveRecording(title: String, hospital: String) = viewModelScope.launch {
        val recording = _state.value.recording
        if (!recording.readyToSave) return@launch
        val file = recording.stoppedFile ?: return@launch
        val result = repository.uploadRecording(file, title, hospital, recording.elapsedMs)
        _state.update { it.copy(snackbar = if (result.isSuccess) "녹음을 저장하고 분석을 시작했습니다." else "로컬에 저장했습니다. 네트워크 연결 시 다시 업로드합니다.") }
        loadConsultations()
    }

    fun sendChat(message: String) = viewModelScope.launch {
        if (message.isBlank()) return@launch
        val assistantIndex = _state.value.chatMessages.size + 1
        _state.update { it.copy(chatMessages = it.chatMessages + (true to message) + (false to ""), chatLoading = true) }
        repository.chat(message) { delta ->
            _state.update { state ->
                val messages = state.chatMessages.toMutableList()
                val old = messages.getOrNull(assistantIndex)?.second.orEmpty()
                if (assistantIndex < messages.size) messages[assistantIndex] = false to (old + delta)
                state.copy(chatMessages = messages)
            }
        }
        _state.update { it.copy(chatLoading = false) }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null) }

    override fun onCleared() {
        recorder?.stop()
        super.onCleared()
    }

    companion object {
        private const val AMPLITUDE_LOG_TAG = "RecorderAmplitude"
        private const val AMPLITUDE_LOG_INTERVAL_MS = 100L
        private const val ELAPSED_UI_INTERVAL_MS = 200L
        private const val WAVEFORM_SAMPLE_COUNT = 48

        private fun emptyWaveform() = List(WAVEFORM_SAMPLE_COUNT) { WaveformBar() }
    }
}
