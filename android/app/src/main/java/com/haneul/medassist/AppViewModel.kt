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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject

data class RecordingUiState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val finalizing: Boolean = false,
    val readyToSave: Boolean = false,
    val elapsedMs: Long = 0,
    val stoppedFile: File? = null,
    val error: String? = null,
    val inputWarning: String? = null,
)

data class AppUiState(
    val home: LoadState<HomeResponse> = LoadState.Idle,
    val medications: List<Medication> = emptyList(),
    val medicationAlarms: List<MedicationAlarm> = emptyList(),
    val medicationDoseRecords: List<MedicationDoseRecord> = emptyList(),
    val selectedExisting: Set<String> = emptySet(),
    val frontPhoto: Uri? = null,
    val backPhoto: Uri? = null,
    val draft: PrescriptionDraft? = null,
    val newMedication: Medication? = null,
    val draftLoading: Boolean = false,
    val interactionAccepted: Accepted? = null,
    val interaction: LoadState<InteractionCheck> = LoadState.Idle,
    val drugSearch: LoadState<DrugProductSearchResponse> = LoadState.Idle,
    val supplementSearch: LoadState<SupplementProductSearchResponse> = LoadState.Idle,
    val selectedMedicationProductCode: String? = null,
    val selectedSupplement: SupplementSearchCandidateDto? = null,
    val selectedSupplementStatementNo: String? = null,
    val supplementInteraction: LoadState<SupplementInteractionCheckResponse> = LoadState.Idle,
    val consultations: LoadState<List<Consultation>> = LoadState.Idle,
    val recording: RecordingUiState = RecordingUiState(),
    val chatMessages: List<Pair<Boolean, String>> = emptyList(),
    val chatLoading: Boolean = false,
    val snackbar: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: MedAssistRepository,
    private val alarmRepository: MedicationAlarmRepository,
    private val ocr: OcrEngine,
    private val savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _waveform = MutableStateFlow(emptyWaveform())
    val waveform: StateFlow<List<WaveformBar>> = _waveform.asStateFlow()
    private var recorder: PcmAacRecorder? = null
    private var supplementSearchJob: Job? = null
    private var drugSearchJob: Job? = null
    private var supplementInteractionJob: Job? = null
    private val supplementInteractionStateMachine = SupplementInteractionRequestStateMachine()
    private val amplitudeProcessor = AmplitudeProcessor()
    private var lastElapsedUiUpdateMs = 0L
    private var lastAmplitudeLogAtMs = 0L
    private var consecutiveSilentInputMs = 0f
    private var inputWarningVisible = false

    init {
        refreshHome()
        loadMedications()
        observeMedicationAlarms()
        observeMedicationDoseRecords()
        loadConsultations()
    }

    private fun observeMedicationAlarms() = viewModelScope.launch {
        alarmRepository.observeAll().collect { alarms ->
            _state.update { it.copy(medicationAlarms = alarms) }
        }
    }

    private fun observeMedicationDoseRecords() = viewModelScope.launch {
        alarmRepository.observeDoseRecords().collect { records ->
            _state.update { it.copy(medicationDoseRecords = records) }
        }
    }

    fun saveMedicationAlarm(
        id: String?,
        medicationId: String,
        medicationName: String,
        hour: Int,
        minute: Int,
        repeatDays: Set<DayOfWeek>,
        timing: String,
        soundEnabled: Boolean,
        soundName: String,
        vibrationEnabled: Boolean,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        val previous = id?.let { alarmRepository.find(it) }
        val currentMedicationName = _state.value.medications
            .firstOrNull { it.id == medicationId }
            ?.name
            ?: medicationName
        alarmRepository.save(
            MedicationAlarm(
                id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                medicationId = medicationId,
                medicationName = currentMedicationName,
                hour = hour,
                minute = minute,
                repeatDays = repeatDays,
                timing = timing,
                soundEnabled = soundEnabled,
                soundName = soundName,
                vibrationEnabled = vibrationEnabled,
                enabled = previous?.enabled ?: true,
            ),
        )
        _state.update { it.copy(snackbar = if (previous == null) "복용 알람을 저장했습니다." else "복용 알람을 수정했습니다.") }
        onSaved()
    }

    fun toggleMedicationAlarm(alarm: MedicationAlarm, enabled: Boolean) = viewModelScope.launch {
        alarmRepository.setEnabled(alarm, enabled)
    }

    fun deleteMedicationAlarm(alarm: MedicationAlarm) = viewModelScope.launch {
        alarmRepository.delete(alarm)
        _state.update { it.copy(snackbar = "복용 알람을 삭제했습니다.") }
    }

    fun deleteMedicationAlarms(alarms: List<MedicationAlarm>, onDeleted: () -> Unit) = viewModelScope.launch {
        alarms.forEach { alarmRepository.delete(it) }
        _state.update { it.copy(snackbar = "선택한 ${alarms.size}개의 알람을 삭제했습니다.") }
        onDeleted()
    }

    fun completeMedicationDose(alarmId: String) = viewModelScope.launch {
        alarmRepository.markCompleted(alarmId)
        _state.update { it.copy(snackbar = "복용 완료로 기록했습니다.") }
    }

    fun cancelMedicationDoseCompletion(alarmId: String) = viewModelScope.launch {
        alarmRepository.markIncomplete(alarmId)
        _state.update { it.copy(snackbar = "복용 완료를 취소했습니다.") }
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

    fun addManualMedication(
        name: String,
        productType: ProductType,
        ingredientDescription: String,
        startDate: String?,
        endDate: String?,
        intakeTiming: String,
        timesPerDay: Int,
        doseValue: Double,
        doseUnit: String,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        val medication = repository.addManualMedication(
            name, productType, ingredientDescription, startDate, endDate, intakeTiming, timesPerDay, doseValue, doseUnit,
        )
        _state.update { current ->
            current.copy(
                medications = (current.medications + medication).distinctBy { it.id },
                selectedExisting = current.selectedExisting + medication.id,
                snackbar = "복용약을 직접 추가했습니다.",
            )
        }
        onSaved()
    }

    fun updateMedication(
        medication: Medication,
        name: String,
        productType: ProductType,
        ingredientDescription: String,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        val updated = repository.updateMedication(medication, name, productType, ingredientDescription)
        alarmRepository.updateMedicationName(updated.id, updated.name)
        _state.update { current ->
            current.copy(
                medications = current.medications.map { if (it.id == updated.id) updated else it },
                snackbar = "복용약 정보를 수정했습니다.",
            )
        }
        onSaved()
    }

    fun deleteMedication(medication: Medication) = viewModelScope.launch {
        repository.deleteMedication(medication)
        alarmRepository.disableForMedication(medication.id)
        _state.update { current ->
            current.copy(
                medications = current.medications.filterNot { it.id == medication.id },
                selectedExisting = current.selectedExisting - medication.id,
                snackbar = "복용 목록에서 삭제하고 연결된 알람을 껐습니다.",
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

    fun resetComparisonCapture() = _state.update {
        it.copy(frontPhoto = null, backPhoto = null, draft = null, newMedication = null, draftLoading = false)
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

    fun searchDrugProducts(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2 || drugSearchJob?.isActive == true) return
        drugSearchJob = viewModelScope.launch {
            _state.update { it.copy(drugSearch = LoadState.Loading) }
            repository.searchDrugProducts(normalized)
                .onSuccess { response ->
                    _state.update {
                        it.copy(drugSearch = if (response.candidates.isEmpty()) LoadState.Empty else LoadState.Content(response))
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(drugSearch = LoadState.Error(error.message ?: "공식 의약품 검색을 완료하지 못했습니다."))
                    }
                }
        }
    }

    fun confirmDraft(onConfirmed: () -> Unit) = viewModelScope.launch {
        val draft = _state.value.draft ?: return@launch
        _state.update { it.copy(draftLoading = true) }
        val medication = repository.confirmDraft(draft)
        supplementInteractionJob?.cancel()
        supplementInteractionStateMachine.reset()
        _state.update {
            it.copy(
                newMedication = medication,
                selectedMedicationProductCode = medication.productCode,
                supplementInteraction = supplementInteractionStateMachine.state,
                draftLoading = false,
            )
        }
        onConfirmed()
    }

    fun searchSupplementProducts(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank() || supplementSearchJob?.isActive == true) return
        supplementSearchJob = viewModelScope.launch {
            _state.update { it.copy(supplementSearch = LoadState.Loading) }
            repository.searchSupplementProducts(normalized)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            supplementSearch = if (response.candidates.isEmpty()) LoadState.Empty else LoadState.Content(response),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(supplementSearch = LoadState.Error(supplementInteractionErrorMessage(error))) }
                }
        }
    }

    fun selectSupplementCandidate(candidate: SupplementSearchCandidateDto) {
        supplementInteractionJob?.cancel()
        supplementInteractionStateMachine.reset()
        _state.update {
            it.copy(
                selectedSupplement = candidate,
                selectedSupplementStatementNo = candidate.sttemntNo,
                supplementInteraction = supplementInteractionStateMachine.state,
            )
        }
    }

    fun checkSupplementInteraction(
        medicationProductCode: String,
        supplementStatementNo: String,
    ): Boolean {
        val key = SupplementInteractionRequestKey(
            medicationProductCode = medicationProductCode.trim(),
            supplementStatementNo = supplementStatementNo.trim(),
        )
        val token = runCatching { supplementInteractionStateMachine.begin(key) }.getOrNull() ?: return false
        _state.update {
            it.copy(
                selectedMedicationProductCode = key.medicationProductCode,
                selectedSupplementStatementNo = key.supplementStatementNo,
                supplementInteraction = supplementInteractionStateMachine.state,
            )
        }
        supplementInteractionJob = viewModelScope.launch {
            repository.checkSupplementInteraction(key.medicationProductCode, key.supplementStatementNo)
                .onSuccess { response ->
                    if (supplementInteractionStateMachine.succeed(token, response)) {
                        _state.update { it.copy(supplementInteraction = supplementInteractionStateMachine.state) }
                    }
                }
                .onFailure { error ->
                    if (supplementInteractionStateMachine.fail(token, supplementInteractionErrorMessage(error))) {
                        _state.update { it.copy(supplementInteraction = supplementInteractionStateMachine.state) }
                    }
                }
        }
        return true
    }

    fun retrySupplementInteraction(): Boolean {
        val medicationCode = _state.value.selectedMedicationProductCode ?: return false
        val supplementCode = _state.value.selectedSupplementStatementNo ?: return false
        return checkSupplementInteraction(medicationCode, supplementCode)
    }

    fun startAnalysis(onStarted: () -> Unit) = viewModelScope.launch {
        val added = _state.value.newMedication ?: return@launch
        val existing = _state.value.medications.filter { it.active && it.id in _state.value.selectedExisting }
        if (existing.isEmpty()) return@launch
        _state.update { it.copy(interaction = LoadState.Loading) }
        val accepted = repository.createCheck()
        savedState["checkId"] = accepted.resourceId
        savedState["jobId"] = accepted.jobId
        _state.update { it.copy(interactionAccepted = accepted) }
        onStarted()
    }

    fun finishAnalysis(onSuccess: () -> Unit) = viewModelScope.launch {
        delay(1_200)
        _state.value.interactionAccepted ?: return@launch
        val added = _state.value.newMedication ?: return@launch
        val existing = _state.value.medications.filter { it.active && it.id in _state.value.selectedExisting }
        runCatching { repository.check(added, existing) }
            .onSuccess {
                _state.update { state -> state.copy(interaction = LoadState.Content(it)) }
                onSuccess()
            }
            .onFailure { error ->
                _state.update { state ->
                    state.copy(interaction = LoadState.Error(error.message ?: "공식 성분·DUR 분석을 완료하지 못했습니다."))
                }
            }
    }

    fun saveInteraction() = viewModelScope.launch {
        val check = (_state.value.interaction as? LoadState.Content)?.value ?: return@launch
        val saved = repository.saveCheck(check)
        _state.update { it.copy(interaction = LoadState.Content(saved), snackbar = "결과를 기록에 저장했습니다.") }
    }

    fun loadConsultations() = viewModelScope.launch {
        _state.update { it.copy(consultations = LoadState.Loading) }
        repository.consultations()
            .onSuccess { values ->
                _state.update { it.copy(consultations = if (values.isEmpty()) LoadState.Empty else LoadState.Content(values)) }
            }
            .onFailure {
                _state.update { it.copy(consultations = LoadState.Error("진료 기록 서버에 연결할 수 없습니다.")) }
            }
    }

    fun startRecording(): Boolean {
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(directory, "consultation-${System.currentTimeMillis()}.m4a")
        amplitudeProcessor.reset()
        lastElapsedUiUpdateMs = 0L
        lastAmplitudeLogAtMs = 0L
        consecutiveSilentInputMs = 0f
        inputWarningVisible = false
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
                    consecutiveSilentInputMs = if (amplitude.dbfs <= AmplitudeProcessor.NOISE_FLOOR_DBFS) {
                        consecutiveSilentInputMs + frameDurationMs
                    } else {
                        0f
                    }
                    val shouldWarnAboutInput = consecutiveSilentInputMs >= SILENT_INPUT_WARNING_MS
                    if (shouldWarnAboutInput != inputWarningVisible) {
                        inputWarningVisible = shouldWarnAboutInput
                        _state.update { state ->
                            state.copy(recording = state.recording.copy(
                                inputWarning = if (shouldWarnAboutInput) {
                                    "마이크 입력이 감지되지 않습니다. 에뮬레이터 호스트 마이크 옵션과 권한을 확인해 주세요."
                                } else null,
                            ))
                        }
                    }
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

                override fun onInputRecovery(reason: String, attempt: Int) {
                    inputWarningVisible = true
                    _state.update { state ->
                        state.copy(recording = state.recording.copy(
                            inputWarning = "$reason 마이크를 자동으로 재연결하고 있습니다. ($attempt/3)",
                        ))
                    }
                }

                override fun onRecordingError(message: String, cause: Throwable?) {
                    if (BuildConfig.DEBUG) Log.e(AMPLITUDE_LOG_TAG, message, cause)
                    _state.update { state ->
                        state.copy(recording = state.recording.copy(active = false, error = message))
                    }
                }

                override fun onRecordingFinalized(
                    recordedDurationMs: Long,
                    quality: PcmAacRecorder.RecordingQuality,
                    error: Throwable?,
                ) {
                    recorder = null
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            AMPLITUDE_LOG_TAG,
                            "finalized durationMs=$recordedDurationMs audibleMs=${quality.audibleDurationMs} " +
                                "trailingSilenceMs=${quality.trailingSilenceMs} maxPeak=${quality.maxPeakAmplitude} " +
                                "systemSilencedMs=${quality.systemSilencedDurationMs}",
                        )
                    }
                    _state.update { state ->
                        state.copy(recording = state.recording.copy(
                            active = false,
                            paused = false,
                            finalizing = false,
                            readyToSave = error == null && recordedDurationMs > 0L,
                            elapsedMs = recordedDurationMs,
                            error = state.recording.error ?: error?.let {
                                if (it is PcmAacRecorder.NoAudioSignalException) it.message
                                else "녹음 파일을 마무리할 수 없습니다: ${it.localizedMessage}"
                            },
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
        _state.update { it.copy(snackbar = if (result.isSuccess) "녹음을 저장하고 AI 기록 생성을 시작했습니다." else "업로드에 실패했습니다. 서버 연결을 확인해 주세요.") }
        val accepted = result.getOrNull()
        if (accepted == null) loadConsultations() else pollConsultation(accepted.resourceId)
    }

    private suspend fun pollConsultation(id: String) {
        repeat(CONSULTATION_POLL_ATTEMPTS) {
            val values = repository.consultations().getOrElse {
                _state.update { state -> state.copy(consultations = LoadState.Error("AI 기록 처리 상태를 확인할 수 없습니다.")) }
                return
            }
            _state.update { state -> state.copy(consultations = if (values.isEmpty()) LoadState.Empty else LoadState.Content(values)) }
            val status = values.firstOrNull { it.id == id }?.status
            if (status == "SUCCEEDED" || status == "FAILED") return
            delay(CONSULTATION_POLL_INTERVAL_MS)
        }
    }

    fun retryConsultation(id: String) = viewModelScope.launch {
        val result = repository.retryConsultation(id)
        if (result.isSuccess) {
            _state.update { it.copy(snackbar = "AI 기록 생성을 다시 시작했습니다.") }
            pollConsultation(id)
        } else {
            _state.update { it.copy(snackbar = "다시 시도할 수 없습니다. 서버를 재시작했다면 새로 녹음해 주세요.") }
        }
    }

    fun sendChat(message: String) = viewModelScope.launch {
        if (message.isBlank()) return@launch
        val officialContext = InteractionChatContextFormatter.format(
            (_state.value.interaction as? LoadState.Content)?.value,
        )
        val assistantIndex = _state.value.chatMessages.size + 1
        _state.update { it.copy(chatMessages = it.chatMessages + (true to message) + (false to ""), chatLoading = true) }
        repository.chat(message, officialContext) { delta ->
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
        supplementSearchJob?.cancel()
        drugSearchJob?.cancel()
        supplementInteractionJob?.cancel()
        recorder?.stop()
        super.onCleared()
    }

    companion object {
        private const val AMPLITUDE_LOG_TAG = "RecorderAmplitude"
        private const val AMPLITUDE_LOG_INTERVAL_MS = 100L
        private const val ELAPSED_UI_INTERVAL_MS = 200L
        private const val SILENT_INPUT_WARNING_MS = 5_000f
        private const val CONSULTATION_POLL_INTERVAL_MS = 2_000L
        private const val CONSULTATION_POLL_ATTEMPTS = 90
        private const val WAVEFORM_SAMPLE_COUNT = 48

        private fun emptyWaveform() = List(WAVEFORM_SAMPLE_COUNT) { WaveformBar() }
    }
}

internal fun supplementInteractionErrorMessage(error: Throwable): String =
    when ((error as? SupplementInteractionRequestException)?.failure) {
        SupplementInteractionTransportFailure.TIMEOUT -> "서버 응답 시간이 초과되었습니다. 다시 시도해 주세요."
        SupplementInteractionTransportFailure.NETWORK -> "약품 서버에 연결할 수 없습니다. 서버와 네트워크 상태를 확인해 주세요."
        SupplementInteractionTransportFailure.MALFORMED_RESPONSE -> "서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."
        SupplementInteractionTransportFailure.HTTP -> "병용 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
        null -> "병용 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
    }
