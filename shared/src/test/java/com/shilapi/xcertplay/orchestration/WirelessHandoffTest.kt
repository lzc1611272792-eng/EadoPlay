package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessHandoffTest {
    @Test
    fun compatibilityRequiresRenderedVideoAndLiveBluetoothControl() {
        fun eligible(
            handoff: Boolean = true,
            frame: Boolean = true,
            session: Boolean = true,
            control: Boolean = true,
            socket: Boolean = true,
            tunnel: Boolean = false,
        ) = canKeepBluetoothCompatibilitySession(handoff, frame, session, control, socket, tunnel)

        assertTrue(eligible())
        assertFalse(eligible(handoff = false))
        assertFalse(eligible(frame = false))
        assertFalse(eligible(session = false))
        assertFalse(eligible(control = false))
        assertFalse(eligible(socket = false))
        assertFalse(eligible(tunnel = true))
    }

    @Test
    fun tunnelFailureMayWaitForVideoOnlyWhileBluetoothBootstrapIsLive() {
        fun canWait(
            handoff: Boolean = true,
            session: Boolean = true,
            control: Boolean = true,
            socket: Boolean = true,
            tunnel: Boolean = false,
        ) = canAwaitBluetoothCompatibilityDecision(handoff, session, control, socket, tunnel)

        assertTrue(canWait())
        assertFalse(canWait(handoff = false))
        assertFalse(canWait(session = false))
        assertFalse(canWait(control = false))
        assertFalse(canWait(socket = false))
        assertFalse(canWait(tunnel = true))
    }

    @Test
    fun activeTunnelKeepsHandoffAliveAfterBluetoothBootstrapCloses() {
        assertTrue(
            isWirelessHandoffInProgress(
                handoffRequested = false,
                tunnelActive = true,
                sessionActive = true,
            ),
        )
    }

    @Test
    fun ordinaryBootstrapLossStillFailsWithoutHandoffState() {
        assertFalse(
            isWirelessHandoffInProgress(
                handoffRequested = false,
                tunnelActive = false,
                sessionActive = false,
            ),
        )
    }
}
