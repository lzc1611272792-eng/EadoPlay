package com.shilapi.xcertplay

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/** Bridges this head unit's MCU mute state to CarPlay pause/play. */
internal class ChanganMuteBridge(
    private val playbackState: () -> Boolean?,
    private val onMediaButton: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: IBinder? = null
    private var lastMute: Boolean? = null
    private var registered = false
    private var suppressedMuteState: Boolean? = null
    private var ignoreMuteUntil = 0L
    private var muteCoupledToPlayback = false

    private val callback = object : Binder() {
        init {
            attachInterface(null, CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            if (code != TRANSACTION_SETTINGS_CHANGED) {
                reply?.writeNoException()
                return true
            }
            return try {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                val mute = if (data.readInt() != 0) readMute(data) else null
                if (mute != null) mainHandler.post { acceptMuteState(mute) }
                reply?.writeNoException()
                true
            } catch (error: Throwable) {
                Log.w(TAG, "failed to decode Changan settings callback", error)
                reply?.writeNoException()
                true
            }
        }
    }

    fun start() {
        if (registered) return
        val binder = findService() ?: run {
            Log.i(TAG, "Changan settings service unavailable; mute bridge disabled")
            return
        }
        // Establish a baseline first; current state is not a new button press.
        lastMute = readCurrentMute(binder)
        registered = transactCallback(binder, TRANSACTION_REGISTER_CALLBACK)
        if (registered) {
            service = binder
            Log.i(TAG, "Changan mute bridge registered baseline=$lastMute")
        } else {
            Log.w(TAG, "Changan mute bridge registration rejected")
        }
    }

    fun stop() {
        val binder = service
        if (registered && binder != null) {
            transactCallback(binder, TRANSACTION_UNREGISTER_CALLBACK)
        }
        registered = false
        service = null
        lastMute = null
        ignoreMuteUntil = 0L
        muteCoupledToPlayback = false
    }

    /** Volume-up/down can implicitly clear MCU mute; that is not a play/pause request. */
    fun noteVolumeKey() {
        ignoreMuteUntil = SystemClock.elapsedRealtime() + VOLUME_UNMUTE_GUARD_MILLIS
    }

    /** Keep UI-originated playback in sync with mute previously created by the wheel. */
    fun onPlaybackStateChanged(playing: Boolean) {
        if (!playing) return
        mainHandler.post {
            if (muteCoupledToPlayback && lastMute == true) {
                muteCoupledToPlayback = false
                Log.i(TAG, "iPhone resumed from UI; clearing wheel-coupled Changan mute")
                alignVehicleMute(false)
            }
        }
    }

    private fun acceptMuteState(mute: Boolean) {
        val previous = lastMute
        lastMute = mute
        if (suppressedMuteState == mute) {
            suppressedMuteState = null
            Log.i(TAG, "accepted internally aligned Changan mute state=$mute")
            return
        }
        if (SystemClock.elapsedRealtime() <= ignoreMuteUntil) {
            if (!mute) muteCoupledToPlayback = false
            Log.i(TAG, "ignored mute state=$mute caused by volume key")
            return
        }
        if (previous == null || previous == mute) return
        val playing = playbackState()
        val mediaButton = carPlayButtonForMuteTransition(previous, mute, playing) ?: return
        val desiredMute = mediaButton == CarPlayMediaButton.PAUSE
        muteCoupledToPlayback = desiredMute
        if (mute != desiredMute) alignVehicleMute(desiredMute)
        Log.i(TAG, "Changan MCU mute changed $previous -> $mute; playing=$playing CarPlay button=$mediaButton")
        onMediaButton(mediaButton)
    }

    private fun alignVehicleMute(mute: Boolean) {
        val binder = service ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SETTINGS_DESCRIPTOR)
            data.writeInt(if (mute) 1 else 0)
            suppressedMuteState = mute
            if (!binder.transact(TRANSACTION_SET_MUTE, data, reply, 0)) {
                suppressedMuteState = null
                return
            }
            reply.readException()
            Log.i(TAG, "aligned Changan mute state to $mute")
        } catch (error: Throwable) {
            suppressedMuteState = null
            Log.w(TAG, "cannot align Changan mute state", error)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun findService(): IBinder? = try {
        val manager = Class.forName("android.os.ServiceManager")
        manager.getMethod("getService", String::class.java)
            .invoke(null, SETTINGS_SERVICE) as? IBinder
    } catch (error: Throwable) {
        Log.w(TAG, "cannot access Changan settings service", error)
        null
    }

    private fun readCurrentMute(binder: IBinder): Boolean? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SETTINGS_DESCRIPTOR)
            if (!binder.transact(TRANSACTION_GET_SETTINGS, data, reply, 0)) return null
            reply.readException()
            if (reply.readInt() != 0) readMute(reply) else null
        } catch (error: Throwable) {
            Log.w(TAG, "cannot read initial Changan mute state", error)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactCallback(binder: IBinder, transaction: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SETTINGS_DESCRIPTOR)
            data.writeStrongBinder(callback)
            if (!binder.transact(transaction, data, reply, 0)) return false
            reply.readException()
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Changan settings callback transaction=$transaction failed", error)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Reads SettingsInfo only through its isMute field (the twelfth parcel field). */
    private fun readMute(parcel: Parcel): Boolean {
        repeat(8) { parcel.readInt() }
        parcel.readString()
        parcel.readInt() // isBrignessAuto
        parcel.readInt() // currentRTC
        return parcel.readInt() != 0
    }

    private companion object {
        const val TAG = "EadoPlay-MuteBridge"
        const val SETTINGS_SERVICE = "coagent.settings"
        const val SETTINGS_DESCRIPTOR = "com.coagent.proxy.binder.service.ISettingInterface"
        const val CALLBACK_DESCRIPTOR = "com.coagent.proxy.binder.callback.ISettingCallBackInterface"
        const val TRANSACTION_GET_SETTINGS = 22
        const val TRANSACTION_SET_MUTE = 13
        const val TRANSACTION_REGISTER_CALLBACK = 25
        const val TRANSACTION_UNREGISTER_CALLBACK = 26
        const val TRANSACTION_SETTINGS_CHANGED = 3
        const val VOLUME_UNMUTE_GUARD_MILLIS = 400L
    }
}

internal fun carPlayButtonForMuteTransition(
    previous: Boolean?,
    current: Boolean,
    playing: Boolean? = null,
): Int? = when {
    previous == null || previous == current -> null
    playing != null -> CarPlayMediaButton.toggleForPlaying(playing)
    current -> CarPlayMediaButton.PAUSE
    else -> CarPlayMediaButton.PLAY
}
