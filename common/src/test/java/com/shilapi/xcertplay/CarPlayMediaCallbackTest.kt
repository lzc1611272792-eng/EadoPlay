package com.shilapi.xcertplay

import android.content.Intent
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.airplay.CarPlayTelephonyButton
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayMediaCallbackTest {
    private val sent = mutableListOf<Int>()
    private val callback = CarPlayMediaCallback { index, _ -> sent += index }

    @Test
    fun controllerPlayAndPauseAreExplicit() {
        callback.onPlay()
        callback.onPause()
        callback.onSkipToNext()
        callback.onSkipToPrevious()

        assertEquals(
            listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE, CarPlayMediaButton.NEXT, CarPlayMediaButton.PREVIOUS),
            sent,
        )
    }

    @Test
    fun hardwarePlayAndPauseKeysKeepTheirMeaning() {
        press(KeyEvent.KEYCODE_MEDIA_PLAY)
        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

        assertEquals(
            listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE, CarPlayMediaButton.PLAY_PAUSE),
            sent,
        )
    }

    @Test
    fun aHeldKeySendsOnePress() {
        press(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 1)
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))

        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    @Test
    fun changanSteeringKeysMapToCarPlayControls() {
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, carPlayButtonForChanganKey("MUTE", "DOWN"))
        assertEquals(CarPlayMediaButton.PREVIOUS, carPlayButtonForChanganKey("PRE", "NONE"))
        assertEquals(CarPlayMediaButton.NEXT, carPlayButtonForChanganKey("NEXT", "DOWN"))
        assertEquals(null, carPlayButtonForChanganKey("VOLUP", "DOWN"))
        assertEquals(null, carPlayButtonForChanganKey("NEXT", "UP"))
    }

    @Test
    fun changanMuteTransitionsMapToExplicitPauseAndPlay() {
        assertEquals(null, carPlayButtonForMuteTransition(null, false))
        assertEquals(null, carPlayButtonForMuteTransition(false, false))
        assertEquals(CarPlayMediaButton.PAUSE, carPlayButtonForMuteTransition(false, true))
        assertEquals(null, carPlayButtonForMuteTransition(true, true))
        assertEquals(CarPlayMediaButton.PLAY, carPlayButtonForMuteTransition(true, false))
        assertEquals(CarPlayMediaButton.PLAY, carPlayButtonForMuteTransition(false, true, playing = false))
        assertEquals(CarPlayMediaButton.PAUSE, carPlayButtonForMuteTransition(true, false, playing = true))
    }

    @Test
    fun changanPhoneKeysMapToCarPlayTelephonyControls() {
        assertEquals(CarPlayTelephonyButton.HOOK_SWITCH, carPlayTelephonyButtonForChanganKey("TEL", "NONE"))
        assertEquals(CarPlayTelephonyButton.DROP, carPlayTelephonyButtonForChanganKey("HANDUP", "NONE"))
        assertEquals(null, carPlayTelephonyButtonForChanganKey("TEL", "UP"))
        assertEquals(null, carPlayTelephonyButtonForChanganKey("MUTE", "NONE"))
    }

    private fun press(keyCode: Int, repeat: Int = 0) {
        for (count in 0..repeat) {
            callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, count)))
        }
    }

    private fun button(event: KeyEvent) = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event)
}
