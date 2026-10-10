package com.shilapi.xcertplay.media

import android.media.MediaRecorder

/** Compatibility policy for Android 4.4 automotive microphone HALs. */
internal object LegacyMicrophoneCapture {
    private const val LEGACY_CAPTURE_RATE = 16_000

    data class Plan(val sampleRate: Int, val audioSource: Int)

    fun plan(sdkInt: Int, audioType: String, negotiatedSampleRate: Int): Plan {
        val voice = audioType == "telephony" || audioType == "speechrecognition"
        return if (sdkInt <= 19 && voice && negotiatedSampleRate > LEGACY_CAPTURE_RATE) {
            // The Eado/i.MX6 HAL exposes a real 16 kHz mono capture path. Asking its Android 4.4
            // AudioRecord layer for 48 kHz VOICE_COMMUNICATION creates a track but read() never
            // yields data. Capture the physical microphone at its native rate and resample below.
            // Match the recorder used by the factory iFlytek client on this ROM. The generic MIC
            // source opens an AudioFlinger track but never delivers samples after the vendor
            // recorder yields; VOICE_RECOGNITION is the known-good HAL route.
            Plan(LEGACY_CAPTURE_RATE, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            val source = when (audioType) {
                "telephony" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                "speechrecognition" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                else -> MediaRecorder.AudioSource.MIC
            }
            Plan(negotiatedSampleRate, source)
        }
    }

    fun captureFrameBytes(frameMillis: Int, sampleRate: Int, channels: Int): Int =
        maxOf(1, sampleRate * frameMillis / 1_000) * channels * 2

    /** Nearest-neighbour PCM16 resampling; the legacy path is the exact, low-cost 16 -> 48 kHz case. */
    fun toWireRate(
        pcm16le: ByteArray,
        inputSampleRate: Int,
        outputSampleRate: Int,
        channels: Int,
    ): ByteArray {
        if (inputSampleRate == outputSampleRate) return pcm16le.copyOf()
        require(inputSampleRate > 0 && outputSampleRate > 0 && channels > 0)
        val bytesPerFrame = channels * 2
        require(pcm16le.size % bytesPerFrame == 0)
        val inputFrames = pcm16le.size / bytesPerFrame
        val outputFrames = (inputFrames.toLong() * outputSampleRate / inputSampleRate).toInt()
        val output = ByteArray(outputFrames * bytesPerFrame)
        for (outputFrame in 0 until outputFrames) {
            val inputFrame = minOf(
                inputFrames - 1,
                (outputFrame.toLong() * inputSampleRate / outputSampleRate).toInt(),
            )
            val inputOffset = inputFrame * bytesPerFrame
            val outputOffset = outputFrame * bytesPerFrame
            pcm16le.copyInto(output, outputOffset, inputOffset, inputOffset + bytesPerFrame)
        }
        return output
    }
}
