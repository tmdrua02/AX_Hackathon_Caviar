package com.haneul.medassist.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/** Records PCM for metering while encoding the same samples to an AAC/M4A file. */
class PcmAacRecorder(
    private val outputFile: File,
    private val listener: Listener,
) {
    interface Listener {
        fun onPcmFrame(samples: ShortArray, sampleCount: Int, frameDurationMs: Float, recordedDurationMs: Long)
        fun onInputRecovery(reason: String, attempt: Int)
        fun onRecordingError(message: String, cause: Throwable? = null)
        fun onRecordingFinalized(recordedDurationMs: Long, quality: RecordingQuality, error: Throwable?)
    }

    data class RecordingQuality(
        val audibleDurationMs: Long,
        val trailingSilenceMs: Long,
        val maxPeakAmplitude: Int,
        val systemSilencedDurationMs: Long,
    )

    class NoAudioSignalException(message: String) : IllegalStateException(message)

    @Volatile private var running = false
    @Volatile private var paused = false
    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var worker: Thread? = null
    private var muxerStarted = false
    private var muxerTrackIndex = -1
    private var encodedSampleCount = 0L
    private var audibleSampleCount = 0L
    private var lastAudibleSamplePosition = 0L
    private var maxPeakAmplitude = 0
    private var systemSilencedSampleCount = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        check(!running) { "Recorder is already running" }
        try {
            audioRecord = createAudioRecord()

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_FRAME_SAMPLES * BYTES_PER_SAMPLE * 2)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            startAudioRecord(checkNotNull(audioRecord))

            running = true
            paused = false
            worker = thread(name = "MedAssistAudioCapture", start = true) { captureAndEncode() }
        } catch (error: Throwable) {
            releaseResources()
            throw error
        }
    }

    fun pause() {
        check(running) { "Recorder is not running" }
        paused = true
    }

    fun resume() {
        check(running) { "Recorder is not running" }
        paused = false
    }

    /** Returns immediately; the worker finishes the M4A container in the background. */
    fun stop() {
        running = false
    }

    private fun captureAndEncode() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val pcm = ShortArray(PCM_FRAME_SAMPLES)
        var consecutiveZeroSampleCount = 0L
        var consecutiveCorruptedSampleCount = 0L
        var consecutiveRecoveryAttempts = 0
        var failure: Throwable? = null
        try {
            while (running) {
                val count = audioRecord?.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    ?: AudioRecord.ERROR_INVALID_OPERATION
                if (!running) break
                if (count <= 0) {
                    throw IllegalStateException("오디오 입력이 중단되었습니다. (code=$count)")
                }
                if (paused) continue

                val inputFrame = analyzeInput(pcm, count)
                val frameIsExactlyZero = inputFrame.peakAmplitude == 0
                val frameLooksCorrupted = inputFrame.peakAmplitude >= CORRUPTED_PEAK_AMPLITUDE &&
                    inputFrame.rmsAmplitude >= CORRUPTED_RMS_AMPLITUDE
                if (frameIsExactlyZero) {
                    consecutiveZeroSampleCount += count
                } else {
                    consecutiveZeroSampleCount = 0L
                }
                if (frameLooksCorrupted) {
                    consecutiveCorruptedSampleCount += count
                } else {
                    consecutiveCorruptedSampleCount = 0L
                }
                if (!frameIsExactlyZero && !frameLooksCorrupted) {
                    consecutiveRecoveryAttempts = 0
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    audioRecord?.activeRecordingConfiguration?.isClientSilenced == true
                ) {
                    systemSilencedSampleCount += count
                }

                val frameDurationMs = count * 1_000f / SAMPLE_RATE
                val frameIsInvalid = frameIsExactlyZero || frameLooksCorrupted
                val nextDurationMs = (encodedSampleCount + if (frameIsInvalid) 0 else count) * 1_000L / SAMPLE_RATE
                listener.onPcmFrame(pcm, count, frameDurationMs, nextDurationMs)

                val recoveryReason = when {
                    consecutiveZeroSampleCount >= INPUT_RECOVERY_SILENCE_SAMPLES ->
                        "마이크 입력이 일시적으로 중단되었습니다."
                    consecutiveCorruptedSampleCount >= INPUT_RECOVERY_CORRUPTED_SAMPLES ->
                        "마이크 입력 파형이 비정상적으로 왜곡되었습니다."
                    else -> null
                }
                if (recoveryReason != null) {
                    consecutiveRecoveryAttempts += 1
                    if (consecutiveRecoveryAttempts > MAX_CONSECUTIVE_INPUT_RECOVERY_ATTEMPTS) {
                        throw NoAudioSignalException(
                            "마이크 입력 장치가 중단되어 자동 재연결에 실패했습니다. 에뮬레이터의 호스트 마이크를 다시 켜 주세요.",
                        )
                    }
                    Log.w(
                        TAG,
                        "$recoveryReason Reconnecting AudioRecord (attempt=$consecutiveRecoveryAttempts)",
                    )
                    listener.onInputRecovery(recoveryReason, consecutiveRecoveryAttempts)
                    if (!reconnectAudioRecord()) break
                    consecutiveZeroSampleCount = 0L
                    consecutiveCorruptedSampleCount = 0L
                }

                // Emulator HAL failures can return either exact zeroes or a repeated full-scale waveform.
                // Never persist those frames into the consultation recording.
                if (frameIsInvalid) continue

                acceptInput(inputFrame, count)
                queuePcm(pcm, count)
                drainEncoder(waitForEnd = false)
            }
        } catch (error: Throwable) {
            failure = error
            listener.onRecordingError("오디오 입력 또는 인코딩이 중단되었습니다.", error)
        } finally {
            runCatching { queueEndOfStream() }.onFailure { if (failure == null) failure = it }
            runCatching { drainEncoder(waitForEnd = true) }.onFailure { if (failure == null) failure = it }
            val durationMs = encodedSampleCount * 1_000L / SAMPLE_RATE
            val quality = RecordingQuality(
                audibleDurationMs = audibleSampleCount * 1_000L / SAMPLE_RATE,
                trailingSilenceMs = (encodedSampleCount - lastAudibleSamplePosition).coerceAtLeast(0L) * 1_000L / SAMPLE_RATE,
                maxPeakAmplitude = maxPeakAmplitude,
                systemSilencedDurationMs = systemSilencedSampleCount * 1_000L / SAMPLE_RATE,
            )
            if (failure == null && !hasUsableAudio(durationMs, quality)) {
                failure = NoAudioSignalException(
                    if (quality.systemSilencedDurationMs >= 2_000L) {
                        "다른 앱 또는 시스템이 마이크 입력을 음소거했습니다. 다른 녹음 앱을 종료한 뒤 다시 시도해 주세요."
                    } else {
                        "녹음에서 유효한 음성을 감지하지 못했습니다. 에뮬레이터는 -allow-host-audio 옵션으로 실행하고 마이크 권한을 확인해 주세요."
                    },
                )
            }
            releaseResources()
            listener.onRecordingFinalized(durationMs, quality, failure)
        }
    }

    private data class InputFrame(
        val peakAmplitude: Int,
        val rmsAmplitude: Double,
    )

    private fun analyzeInput(samples: ShortArray, sampleCount: Int): InputFrame {
        var sumOfSquares = 0.0
        var framePeak = 0
        for (index in 0 until sampleCount) {
            val value = samples[index].toInt()
            sumOfSquares += value.toDouble() * value.toDouble()
            framePeak = max(framePeak, kotlin.math.abs(value))
        }
        val rms = kotlin.math.sqrt(sumOfSquares / sampleCount.coerceAtLeast(1))
        return InputFrame(framePeak, rms)
    }

    private fun acceptInput(inputFrame: InputFrame, sampleCount: Int) {
        maxPeakAmplitude = max(maxPeakAmplitude, inputFrame.peakAmplitude)
        if (inputFrame.rmsAmplitude >= AUDIBLE_RMS_AMPLITUDE) {
            audibleSampleCount += sampleCount
            lastAudibleSamplePosition = encodedSampleCount + sampleCount
        }
    }

    private fun hasUsableAudio(durationMs: Long, quality: RecordingQuality): Boolean {
        return RecordingQualityEvaluator.isUsable(
            durationMs = durationMs,
            audibleDurationMs = quality.audibleDurationMs,
            trailingSilenceMs = quality.trailingSilenceMs,
            maxPeakAmplitude = quality.maxPeakAmplitude,
            systemSilencedDurationMs = quality.systemSilencedDurationMs,
        )
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferBytes > 0) { "사용 가능한 마이크 입력 장치를 찾을 수 없습니다." }
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBufferBytes, PCM_FRAME_SAMPLES * BYTES_PER_SAMPLE * 4))
            .build()
            .also { check(it.state == AudioRecord.STATE_INITIALIZED) { "마이크 입력을 초기화할 수 없습니다." } }
    }

    private fun startAudioRecord(record: AudioRecord) {
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "마이크 입력이 시작되지 않았습니다."
        }
    }

    private fun reconnectAudioRecord(): Boolean {
        val previous = audioRecord
        runCatching {
            if (previous?.recordingState == AudioRecord.RECORDSTATE_RECORDING) previous.stop()
        }
        runCatching { previous?.release() }
        audioRecord = null
        if (!running) return false

        Thread.sleep(INPUT_RECOVERY_DELAY_MS)
        if (!running) return false
        val replacement = createAudioRecord()
        return try {
            startAudioRecord(replacement)
            audioRecord = replacement
            true
        } catch (error: Throwable) {
            runCatching { replacement.release() }
            throw error
        }
    }

    private fun queuePcm(samples: ShortArray, sampleCount: Int) {
        var sourceOffset = 0
        while (sourceOffset < sampleCount) {
            val codec = encoder ?: error("AAC encoder is unavailable")
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainEncoder(waitForEnd = false)
                continue
            }
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("AAC input buffer is unavailable")
            inputBuffer.clear()
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val samplesToWrite = min(inputBuffer.remaining() / BYTES_PER_SAMPLE, sampleCount - sourceOffset)
            for (index in 0 until samplesToWrite) inputBuffer.putShort(samples[sourceOffset + index])
            val presentationTimeUs = encodedSampleCount * 1_000_000L / SAMPLE_RATE
            codec.queueInputBuffer(
                inputIndex,
                0,
                samplesToWrite * BYTES_PER_SAMPLE,
                presentationTimeUs,
                0,
            )
            sourceOffset += samplesToWrite
            encodedSampleCount += samplesToWrite
        }
    }

    private fun queueEndOfStream() {
        val codec = encoder ?: return
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                val presentationTimeUs = encodedSampleCount * 1_000_000L / SAMPLE_RATE
                codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            drainEncoder(waitForEnd = false)
        }
    }

    private fun drainEncoder(waitForEnd: Boolean) {
        val codec = encoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var emptyPolls = 0
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEnd || ++emptyPolls >= MAX_END_OF_STREAM_POLLS) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "AAC output format changed twice" }
                    muxerTrackIndex = muxer?.addTrack(codec.outputFormat)
                        ?: error("M4A muxer is unavailable")
                    muxer?.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    emptyPolls = 0
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                        ?: error("AAC output buffer is unavailable")
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                    if (bufferInfo.size > 0) {
                        check(muxerStarted) { "M4A muxer has not started" }
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                    }
                    val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (endOfStream) return
                }
            }
        }
    }

    private fun releaseResources() {
        runCatching {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord?.stop()
        }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        muxerStarted = false
        worker = null
    }

    companion object {
        private const val TAG = "PcmAacRecorder"
        const val SAMPLE_RATE = 48_000
        const val UI_FRAME_RATE = 30
        const val PCM_FRAME_SAMPLES = SAMPLE_RATE / UI_FRAME_RATE
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val AUDIO_BIT_RATE = 128_000
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val MAX_END_OF_STREAM_POLLS = 100
        private const val AUDIBLE_RMS_AMPLITUDE = 104.0 // approximately -50 dBFS
        private const val INPUT_RECOVERY_SILENCE_MS = 2_000L
        private const val INPUT_RECOVERY_SILENCE_SAMPLES = SAMPLE_RATE * INPUT_RECOVERY_SILENCE_MS / 1_000L
        private const val INPUT_RECOVERY_CORRUPTED_MS = 1_000L
        private const val INPUT_RECOVERY_CORRUPTED_SAMPLES =
            SAMPLE_RATE * INPUT_RECOVERY_CORRUPTED_MS / 1_000L
        private const val CORRUPTED_PEAK_AMPLITUDE = 32_767
        private const val CORRUPTED_RMS_AMPLITUDE = 21_000.0
        private const val INPUT_RECOVERY_DELAY_MS = 2_000L
        private const val MAX_CONSECUTIVE_INPUT_RECOVERY_ATTEMPTS = 3
    }
}
