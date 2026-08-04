package com.haneul.medassist.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Process
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
        fun onRecordingError(message: String, cause: Throwable? = null)
        fun onRecordingFinalized(recordedDurationMs: Long, error: Throwable?)
    }

    @Volatile private var running = false
    @Volatile private var paused = false
    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var worker: Thread? = null
    private var muxerStarted = false
    private var muxerTrackIndex = -1
    private var encodedSampleCount = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        check(!running) { "Recorder is already running" }
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferBytes > 0) { "사용 가능한 마이크 입력 장치를 찾을 수 없습니다." }

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBufferBytes, PCM_FRAME_SAMPLES * BYTES_PER_SAMPLE * 4))
                .build()
            check(audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                "마이크 입력을 초기화할 수 없습니다."
            }

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
            audioRecord?.startRecording()
            check(audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "마이크 입력이 시작되지 않았습니다."
            }

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

                val frameDurationMs = count * 1_000f / SAMPLE_RATE
                val nextDurationMs = (encodedSampleCount + count) * 1_000L / SAMPLE_RATE
                listener.onPcmFrame(pcm, count, frameDurationMs, nextDurationMs)
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
            releaseResources()
            listener.onRecordingFinalized(durationMs, failure)
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
        const val SAMPLE_RATE = 44_100
        const val UI_FRAME_RATE = 30
        const val PCM_FRAME_SAMPLES = SAMPLE_RATE / UI_FRAME_RATE
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val AUDIO_BIT_RATE = 128_000
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val MAX_END_OF_STREAM_POLLS = 100
    }
}
