package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/**
 * Hardware media keys → CarPlay media HID presses (indices into [AirPlayHid]'s media report).
 */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST

    /** Prefer an explicit command; toggle is only a fallback before iPhone state is known. */
    fun toggleForPlaying(playing: Boolean?): Int = when (playing) {
        true -> PAUSE
        false -> PLAY
        null -> PLAY_PAUSE
    }

    /** The CarPlay press for [keyCode], or null when the key is not a media key CarPlay handles. */
    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PREVIOUS
        KeyEvent.KEYCODE_MEDIA_PLAY -> PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PLAY_PAUSE
        else -> null
    }
}

/** Indices into [AirPlayHid]'s telephony report. */
object CarPlayTelephonyButton {
    const val HOOK_SWITCH = 1
    const val DROP = 3

    /** Android-standard call keys only; vendor-specific wheel codes must be confirmed from logs. */
    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_HEADSETHOOK -> HOOK_SWITCH
        KeyEvent.KEYCODE_ENDCALL -> DROP
        else -> null
    }
}
