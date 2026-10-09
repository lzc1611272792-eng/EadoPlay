package com.shilapi.xcertplay.orchestration

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RfcommCompatibilityTest {
    @Test
    fun retriesLegacyBindCollision() {
        assertTrue(
            shouldRetryWithInsecureRfcomm(
                IOException("bind failed: EADDRINUSE (Address already in use)"),
            ),
        )
    }

    @Test
    fun findsBindCollisionInCauseChain() {
        assertTrue(
            shouldRetryWithInsecureRfcomm(
                IOException("Could not connect RFCOMM", IOException("Address already in use")),
            ),
        )
    }

    @Test
    fun doesNotDowngradeUnrelatedFailures() {
        assertFalse(shouldRetryWithInsecureRfcomm(IOException("Service discovery failed")))
    }
}
