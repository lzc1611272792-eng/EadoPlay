package com.shilapi.xcertplay.media

import android.media.MediaRecorder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyMicrophoneCaptureTest {
    @Test
    fun `api 19 telephony captures native 16 kHz microphone`() {
        val plan = LegacyMicrophoneCapture.plan(19, "telephony", 48_000)

        assertEquals(16_000, plan.sampleRate)
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, plan.audioSource)
        assertEquals(640, LegacyMicrophoneCapture.captureFrameBytes(20, plan.sampleRate, 1))
    }

    @Test
    fun `16 kHz mono frame expands exactly to 48 kHz`() {
        val input = byteArrayOf(1, 2, 3, 4)

        val output = LegacyMicrophoneCapture.toWireRate(input, 16_000, 48_000, 1)

        assertArrayEquals(
            byteArrayOf(1, 2, 1, 2, 1, 2, 3, 4, 3, 4, 3, 4),
            output,
        )
    }

    @Test
    fun `modern telephony keeps negotiated capture path`() {
        val plan = LegacyMicrophoneCapture.plan(23, "telephony", 48_000)

        assertEquals(48_000, plan.sampleRate)
        assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION, plan.audioSource)
    }
}
