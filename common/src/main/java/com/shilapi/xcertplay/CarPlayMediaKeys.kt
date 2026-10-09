package com.shilapi.xcertplay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RemoteControlClient
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.airplay.CarPlayTelephonyButton
import com.shilapi.xcertplay.orchestration.CarPlayController

/** Steering-wheel media keys with equivalent API 19 and API 21+ backends. */
internal object CarPlayMediaKeys {
    private const val TAG = "EadoPlay-MediaKeys"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backend: Backend by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Api21Backend() else LegacyBackend()
    }

    fun attach(context: Context, controller: CarPlayController) = backend.attach(context, controller)
    fun detach(expected: CarPlayController?) = backend.detach(expected)
    fun onMediaAudioChanged(active: Boolean) = mainHandler.post { backend.update(active) }
    fun onIphonePlaying(playing: Boolean) {
        if (playing) mainHandler.post { backend.regainFocus() }
    }

    internal fun dispatch(event: KeyEvent): Boolean = backend.dispatch(event)

    private interface Backend {
        fun attach(context: Context, controller: CarPlayController)
        fun detach(expected: CarPlayController?)
        fun update(active: Boolean)
        fun regainFocus()
        fun dispatch(event: KeyEvent): Boolean
    }

    private abstract class FocusBackend : Backend {
        protected var context: Context? = null
        protected var controller: CarPlayController? = null
        private var changanReceiver: BroadcastReceiver? = null
        private var changanMuteBridge: ChanganMuteBridge? = null
        private var iphonePlaying: Boolean? = null
        private var focusHeld = false
        private var lastCommand = ""
        private var lastCommandAt = 0L
        private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            Log.i(TAG, "audio focus change=$change")
            if (change == AudioManager.AUDIOFOCUS_LOSS) focusHeld = false
        }

        override fun attach(context: Context, controller: CarPlayController) {
            if (this.controller !== controller) release()
            this.context = context.applicationContext
            this.controller = controller
            controller.playbackListener = ::onPlaybackChanged
            if (changanMuteBridge == null) {
                changanMuteBridge = ChanganMuteBridge(
                    playbackState = { iphonePlaying },
                    onMediaButton = ::sendMedia,
                ).also { it.start() }
            }
            if (changanReceiver == null) {
                changanReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val keyCode = intent.getStringExtra(CHANGAN_KEY_CODE_EXTRA)
                        val keyState = intent.getStringExtra(CHANGAN_KEY_STATE_EXTRA)
                        Log.i(TAG, "Changan steering raw key=$keyCode state=$keyState")
                        if (!isChanganKeyDown(keyState)) return
                        if (keyCode == "VOLUP" || keyCode == "VOLDOWN") {
                            changanMuteBridge?.noteVolumeKey()
                        }
                        val handled = when (keyCode) {
                            // The iPhone's NowPlaying state can arrive late on this Android 4.4
                            // head unit.  A true HID toggle stays correct even when that cached
                            // state is stale, and mirrors the behaviour of the working Lite build.
                            "MUTE" -> sendMedia(CarPlayMediaButton.PLAY_PAUSE)
                            "PRE" -> sendMedia(CarPlayMediaButton.PREVIOUS)
                            "NEXT" -> sendMedia(CarPlayMediaButton.NEXT)
                            "TEL" -> sendTelephony(CarPlayTelephonyButton.HOOK_SWITCH)
                            "HANDUP" -> sendTelephony(CarPlayTelephonyButton.DROP)
                            else -> {
                                Log.i(TAG, "unmapped Changan steering key=$keyCode state=$keyState")
                                false
                            }
                        }
                        Log.i(TAG, "Changan steering key=$keyCode state=$keyState handled=$handled")
                        if (handled && isOrderedBroadcast) abortBroadcast()
                    }
                }.also {
                    this.context?.registerReceiver(
                        it,
                        IntentFilter(CHANGAN_KEY_ACTION).apply { priority = Int.MAX_VALUE },
                    )
                }
            }
        }

        override fun detach(expected: CarPlayController?) {
            if (expected == null || controller !== expected) return
            expected.playbackListener = null
            release()
        }

        override fun regainFocus() {
            if (!shouldRequestAudioFocus()) {
                Log.i(TAG, "Android 4.4 compatibility: skip STREAM_MUSIC audio focus request")
                return
            }
            if (focusHeld) return
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            val result = audio.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.i(TAG, "audio focus request result=$result held=$focusHeld")
        }

        protected open fun shouldRequestAudioFocus(): Boolean = true

        override fun dispatch(event: KeyEvent): Boolean {
            val telephony = CarPlayTelephonyButton.forKeyCode(event.keyCode)
            if (telephony != null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) sendTelephony(telephony)
                return true
            }
            val media = CarPlayMediaButton.forKeyCode(event.keyCode) ?: return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                sendMedia(media)
            }
            return true
        }

        protected fun sendMedia(index: Int): Boolean = sendDeduplicated("media:$index") {
            controller?.sendMediaButton(index) ?: false
        }

        private fun sendTelephony(index: Int): Boolean = sendDeduplicated("phone:$index") {
            controller?.sendTelephonyButton(index) ?: false
        }

        private inline fun sendDeduplicated(command: String, send: () -> Boolean): Boolean {
            val now = android.os.SystemClock.elapsedRealtime()
            if (command == lastCommand && now - lastCommandAt < MEDIA_KEY_DEDUP_MILLIS) {
                Log.i(TAG, "duplicate steering command ignored command=$command")
                return true
            }
            lastCommand = command
            lastCommandAt = now
            val sent = send()
            Log.i(TAG, "steering command -> CarPlay $command sent=$sent")
            return sent
        }

        protected open fun release() {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audio?.abandonAudioFocus(focusListener)
            changanReceiver?.let { receiver ->
                try {
                    context?.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Already removed while the activity was closing.
                }
            }
            changanReceiver = null
            changanMuteBridge?.stop()
            changanMuteBridge = null
            focusHeld = false
            lastCommand = ""
            lastCommandAt = 0L
            iphonePlaying = null
            controller = null
            context = null
        }

        private fun onPlaybackChanged(playing: Boolean) {
            iphonePlaying = playing
            Log.i(TAG, "iPhone playback state playing=$playing")
            changanMuteBridge?.onPlaybackStateChanged(playing)
            if (playing) mainHandler.post { regainFocus() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class Api21Backend : FocusBackend() {
        private var session: MediaSession? = null

        override fun update(active: Boolean) {
            val currentContext = context ?: return
            if (controller == null) return
            if (session == null) {
                session = MediaSession(currentContext, "EadoPlay CarPlay").apply {
                    setCallback(CarPlayMediaCallback { index, _ -> sendMedia(index) }, mainHandler)
                    isActive = true
                }
            }
            if (active) regainFocus()
            val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            session?.setPlaybackState(
                PlaybackState.Builder().setActions(actions).setState(
                    if (active) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                ).build(),
            )
        }

        override fun release() {
            session?.apply { isActive = false; release() }
            session = null
            super.release()
        }
    }

    @Suppress("DEPRECATION")
    private class LegacyBackend : FocusBackend() {
        private var remote: RemoteControlClient? = null
        private var receiver: ComponentName? = null

        // Some Android 4.4 head units react to losing STREAM_MUSIC focus by
        // sending AVRCP PAUSE to the paired iPhone. AudioTrack can still play
        // the CarPlay stream without owning global audio focus, so avoid that
        // OEM Bluetooth feedback loop on API 19 only.
        override fun shouldRequestAudioFocus(): Boolean =
            Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT

        override fun update(active: Boolean) {
            val currentContext = context ?: return
            val audio = currentContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (remote == null) {
                receiver = ComponentName(currentContext, CarPlayMediaButtonReceiver::class.java)
                val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver)
                val pending = PendingIntent.getBroadcast(currentContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                remote = RemoteControlClient(pending).apply {
                    setTransportControlFlags(
                        RemoteControlClient.FLAG_KEY_MEDIA_PLAY or RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or
                            RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                            RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS,
                    )
                }
                audio.registerMediaButtonEventReceiver(receiver)
                audio.registerRemoteControlClient(remote)
            }
            if (active) regainFocus()
            remote?.setPlaybackState(
                if (active) RemoteControlClient.PLAYSTATE_PLAYING else RemoteControlClient.PLAYSTATE_PAUSED,
            )
        }

        override fun release() {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio != null) {
                remote?.let { audio.unregisterRemoteControlClient(it) }
                receiver?.let { audio.unregisterMediaButtonEventReceiver(it) }
            }
            remote = null
            receiver = null
            super.release()
        }
    }
}

private const val CHANGAN_KEY_ACTION = "com.coagent.intent.action.KEY_CHANGED"
private const val CHANGAN_KEY_CODE_EXTRA = "Key_code"
private const val CHANGAN_KEY_STATE_EXTRA = "Key_state"
private const val MEDIA_KEY_DEDUP_MILLIS = 250L

internal fun carPlayButtonForChanganKey(keyCode: String?, keyState: String?): Int? {
    if (!isChanganKeyDown(keyState)) return null
    return when (keyCode) {
        "MUTE" -> CarPlayMediaButton.PLAY_PAUSE
        "PRE" -> CarPlayMediaButton.PREVIOUS
        "NEXT" -> CarPlayMediaButton.NEXT
        else -> null
    }
}

internal fun carPlayTelephonyButtonForChanganKey(keyCode: String?, keyState: String?): Int? {
    if (!isChanganKeyDown(keyState)) return null
    return when (keyCode) {
        "TEL" -> CarPlayTelephonyButton.HOOK_SWITCH
        "HANDUP" -> CarPlayTelephonyButton.DROP
        else -> null
    }
}

private fun isChanganKeyDown(keyState: String?): Boolean = keyState == "DOWN" || keyState == "NONE"

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class CarPlayMediaCallback(
    private val send: (Int, Bundle?) -> Unit,
) : MediaSession.Callback() {
    override fun onPlay() = send(CarPlayMediaButton.PLAY, null)
    override fun onPause() = send(CarPlayMediaButton.PAUSE, null)
    override fun onSkipToNext() = send(CarPlayMediaButton.NEXT, null)
    override fun onSkipToPrevious() = send(CarPlayMediaButton.PREVIOUS, null)

    override fun onMediaButtonEvent(intent: Intent): Boolean {
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        val index = CarPlayMediaButton.forKeyCode(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) send(index, null)
        return true
    }
}

class CarPlayMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (CarPlayMediaKeys.dispatch(event)) abortBroadcast()
    }
}
