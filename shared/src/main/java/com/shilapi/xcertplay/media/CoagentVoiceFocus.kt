package com.shilapi.xcertplay.media

import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporarily yields the Changan/Coagent speech client's microphone while CarPlay needs uplink.
 *
 * The 2018 Eado firmware keeps iFlytek's AudioRecord active even while its UI is idle. Android 4.4
 * only permits one effective recorder, so a second AudioRecord can start yet never return samples.
 * This uses the vendor's `requestMicWithBt` relay. Calling `coagent.voice` directly is ineffective:
 * iFlyMidware intentionally accepts voice-focus changes only from the `com.coagent.service` process.
 * The relay performs that trusted call and deliberately does not stop a process.
 */
internal object CoagentVoiceFocus {
    private const val TAG = "xcertplay-usb"
    private const val SERVICE_NAME = "coagent.receiver"
    private const val RECEIVER_DESCRIPTOR = "com.coagent.service.IReceiverService"
    private const val REQUEST_MIC_WITH_BT = 2
    private const val ACQUIRE_SETTLE_MILLIS = 350L

    private var activeLeases = 0

    @Synchronized
    fun acquire(report: (String) -> Unit): Closeable? {
        if (activeLeases == 0) {
            if (!requestMicrophone(request = true, report = report)) return null
            // Give the vendor recorder time to leave AudioFlinger before AudioRecord is created.
            SystemClock.sleep(ACQUIRE_SETTLE_MILLIS)
        }
        activeLeases += 1
        return Lease(report)
    }

    @Synchronized
    private fun release(report: (String) -> Unit) {
        if (activeLeases <= 0) return
        activeLeases -= 1
        if (activeLeases == 0) requestMicrophone(request = false, report = report)
    }

    private fun requestMicrophone(request: Boolean, report: (String) -> Unit): Boolean {
        val receiver = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            getService.invoke(null, SERVICE_NAME) as? IBinder
        } catch (error: Throwable) {
            Log.w(TAG, "vendor voice service lookup failed", error)
            report("Microphone: vendor voice focus unavailable error=${error.javaClass.simpleName}")
            return false
        }
        if (receiver == null || !receiver.isBinderAlive) {
            report("Microphone: vendor voice focus service unavailable")
            return false
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(RECEIVER_DESCRIPTOR)
            data.writeInt(if (request) 1 else 0)
            check(receiver.transact(REQUEST_MIC_WITH_BT, data, reply, 0)) {
                "requestMicWithBt transaction rejected"
            }
            reply.readException()
            val action = if (request) "requested" else "released"
            Log.i(TAG, "vendor microphone $action through Coagent receiver")
            report("Microphone: vendor microphone $action through Coagent receiver")
            true
        } catch (error: Throwable) {
            Log.w(TAG, "vendor microphone request failed request=$request", error)
            report(
                "Microphone: vendor microphone ${if (request) "request" else "release"} " +
                    "failed error=${error.javaClass.simpleName}",
            )
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private class Lease(private val report: (String) -> Unit) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) release(report)
        }
    }
}
