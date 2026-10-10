package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AndroidMediaSinkStateTest {
    @Test fun recreatingTheScreenRestoresItsActiveVideoState() {
        val sink = AndroidMediaSink()
        sink.onScreenStreamActive(110, true)
        sink.onScreenStreamActive(111, true)
        sink.onScreenStreamActive(111, false)
        val events = mutableListOf<Pair<Int, Boolean>>()
        sink.setScreenStreamActiveChangedListener { type, active -> events.add(type to active) }
        assertEquals(listOf(110 to true), events)
        sink.close()
        assertEquals(listOf(110 to true, 110 to false), events)
        val afterClose = mutableListOf<Pair<Int, Boolean>>()
        sink.setScreenStreamActiveChangedListener { type, active -> afterClose.add(type to active) }
        assertTrue(afterClose.isEmpty())
    }

    @Test fun softwareAudioGainCombinesMusicLevelWithCarPlayDucking() {
        assertEquals(0.75f, AudioOutputGain.factor(75), 0.0001f)
        assertEquals(0.30f, AudioOutputGain.combine(0.75f, 0.40f), 0.0001f)
    }

    @Test fun softwareAudioGainNeverAmplifiesOrAcceptsUnsafeValues() {
        assertEquals(0.20f, AudioOutputGain.factor(1), 0.0001f)
        assertEquals(1.00f, AudioOutputGain.factor(140), 0.0001f)
        assertEquals(1.00f, AudioOutputGain.combine(1.00f, 2.00f), 0.0001f)
    }

    @Test fun pcmGainAttenuatesPositiveAndNegativeSamplesInPlace() {
        val pcm = byteArrayOf(0x00, 0x40, 0x00, 0xc0.toByte()) // +16384, -16384

        applyPcm16GainInPlace(pcm, 0, pcm.size, 0.5f)

        assertArrayEquals(byteArrayOf(0x00, 0x20, 0x00, 0xe0.toByte()), pcm)
    }

    @Test fun unityPcmGainDoesNotAlterSamples() {
        val pcm = byteArrayOf(0x34, 0x12, 0xcc.toByte(), 0xed.toByte())
        val original = pcm.copyOf()

        applyPcm16GainInPlace(pcm, 0, pcm.size, 1f)

        assertArrayEquals(original, pcm)
    }
}
