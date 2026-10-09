package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDuckingTest {
    @Test
    fun zeroDbKeepsFullVolume() {
        assertEquals(1f, audioGainForDb(0.0), 0.0001f)
    }

    @Test
    fun minusTwentyDbUsesTenPercentGain() {
        assertEquals(0.1f, audioGainForDb(-20.0), 0.0001f)
    }

    @Test
    fun positiveValuesCannotAmplifyPastFullVolume() {
        assertEquals(1f, audioGainForDb(6.0), 0.0001f)
    }
}
