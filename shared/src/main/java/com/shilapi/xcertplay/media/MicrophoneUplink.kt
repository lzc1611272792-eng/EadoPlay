package com.shilapi.xcertplay.media

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.bindWildcardDatagram
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import com.shilapi.xcertplay.airplay.toHexString
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures one PCM microphone stream and sends it back to the phone as sealed CarPlay RTP.
 *
 * The recorder runs only while the matching audio stream is active, so callers start this after
 * the first downlink audio packet and close it on stream teardown.
 */
@android.annotation.SuppressLint("MissingPermission")
internal class MicrophoneUplink(
    private val config: MicrophoneConfig,
    private val report: (String) -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val firstPacketLogged = AtomicBoolean(false)
    private val firstReadLogged = AtomicBoolean(false)
    private val firstFrameLogged = AtomicBoolean(false)
    private var emptyEncodedFrames = 0
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var opusEncoder: OpusEncoder? = null
    @Volatile private var vendorVoiceFocusLease: Closeable? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true

        vendorVoiceFocusLease = CoagentVoiceFocus.acquire(report)

        val capturePlan = LegacyMicrophoneCapture.plan(
            sdkInt = Build.VERSION.SDK_INT,
            audioType = config.audioType,
            negotiatedSampleRate = config.sampleRate,
        )
        val channelMask = if (config.channels >= 2) {
            AndroidAudioFormat.CHANNEL_IN_STEREO
        } else {
            AndroidAudioFormat.CHANNEL_IN_MONO
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            capturePlan.sampleRate,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "microphone unavailable rate=${capturePlan.sampleRate} channels=${config.channels}")
            report("Microphone: unavailable rate=${capturePlan.sampleRate} channels=${config.channels}")
            running.set(false)
            return false
        }

        val source = capturePlan.audioSource
        val nextEncoder = if (config.codec == AudioCodecKind.OPUS) {
            OpusEncoder(config.bitrate ?: 48_000).takeIf { it.available }
        } else {
            null
        }
        if (config.codec == AudioCodecKind.OPUS && nextEncoder == null) {
            Log.w(TAG, "microphone Opus encoder is unavailable")
            report("Microphone: Opus encoder unavailable")
            running.set(false)
            return false
        }
        if (config.codec == AudioCodecKind.OPUS) {
            report("Microphone: Opus encoder backend=${nextEncoder?.backend} frameBytes=${config.frameBytes}")
        }
        val captureFrameBytes = LegacyMicrophoneCapture.captureFrameBytes(
            frameMillis = config.frameMillis,
            sampleRate = capturePlan.sampleRate,
            channels = config.channels,
        )
        val bufferSize = maxOf(minBuffer * 2, captureFrameBytes * 4)
        val nextRecorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AndroidAudioFormat.Builder()
                            .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(capturePlan.sampleRate)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    source,
                    capturePlan.sampleRate,
                    channelMask,
                    AndroidAudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "microphone recorder creation failed", error)
            report("Microphone: recorder creation failed error=${error.javaClass.simpleName}")
            nextEncoder?.close()
            running.set(false)
            return false
        }
        if (nextRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "microphone recorder failed to initialize")
            report("Microphone: recorder failed to initialize source=$source")
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        val nextSocket = try {
            bindWildcardDatagram("airplay microphone")
        } catch (error: Exception) {
            Log.e(TAG, "microphone socket creation failed", error)
            report("Microphone: socket creation failed error=${error.javaClass.simpleName}")
            nextRecorder.release()
            nextEncoder?.close()
            running.set(false)
            return false
        }

        recorder = nextRecorder
        socket = nextSocket
        opusEncoder = nextEncoder
        return try {
            nextRecorder.startRecording()
            thread = Thread({ capture(nextRecorder, nextSocket, capturePlan, captureFrameBytes) }, "carplay-mic").apply {
                isDaemon = true
                start()
            }
            Log.i(
                TAG,
                "microphone uplink started type=${config.audioType} " +
                    "captureRate=${capturePlan.sampleRate} wireRate=${config.sampleRate} " +
                    "channels=${config.channels} " +
                    "frameMs=${config.frameMillis} port=${config.port}",
            )
            report(
                "Microphone: recording started audioType=${config.audioType} " +
                    "source=$source captureRate=${capturePlan.sampleRate} " +
                    "wireRate=${config.sampleRate} codec=${config.codec}",
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "microphone recording failed", error)
            report("Microphone: recording failed error=${error.javaClass.simpleName}")
            release()
            false
        }
    }

    private fun capture(
        recorder: AudioRecord,
        socket: DatagramSocket,
        capturePlan: LegacyMicrophoneCapture.Plan,
        captureFrameBytes: Int,
    ) {
        val capturedFrame = ByteArray(captureFrameBytes)
        val readBuffer = ByteArray(maxOf(capturedFrame.size, MIN_READ_BYTES))
        val counters = MicrophoneCounters()
        var filled = 0
        try {
            report("Microphone: waiting for PCM samples captureRate=${capturePlan.sampleRate}")
            while (running.get()) {
                val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                } else {
                    recorder.read(readBuffer, 0, readBuffer.size)
                }
                if (count < 0) {
                    if (running.get()) Log.e(TAG, "microphone read failed code=$count")
                    if (running.get()) report("Microphone: read failed code=$count")
                    return
                }
                if (count == 0) {
                    continue
                }
                if (firstReadLogged.compareAndSet(false, true)) {
                    report("Microphone: first PCM samples read bytes=$count")
                }
                var offset = 0
                while (offset < count && running.get()) {
                    val copied = minOf(capturedFrame.size - filled, count - offset)
                    readBuffer.copyInto(capturedFrame, filled, offset, offset + copied)
                    filled += copied
                    offset += copied
                    if (filled == capturedFrame.size) {
                        val wireFrame = LegacyMicrophoneCapture.toWireRate(
                            pcm16le = capturedFrame,
                            inputSampleRate = capturePlan.sampleRate,
                            outputSampleRate = config.sampleRate,
                            channels = config.channels,
                        )
                        sendFrame(socket, counters, wireFrame)
                        filled = 0
                    }
                }
            }
        } catch (error: Exception) {
            if (running.get()) Log.e(TAG, "microphone capture failed", error)
            if (running.get()) report("Microphone: capture failed error=${error.javaClass.simpleName}")
        } finally {
            running.set(false)
            release()
        }
    }

    private fun sendFrame(socket: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        if (firstFrameLogged.compareAndSet(false, true)) {
            report("Microphone: first PCM frame ready bytes=${frame.size} expected=${config.frameBytes}")
        }
        val bodies = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoder?.encode(frame).orEmpty()
        } else {
            listOf(MicrophonePacketizer.toWirePcm(frame))
        }
        if (bodies.isEmpty()) {
            emptyEncodedFrames++
            if (emptyEncodedFrames == 1 || emptyEncodedFrames == 50) {
                report("Microphone: encoder produced no packet frames=$emptyEncodedFrames backend=${opusEncoder?.backend}")
            }
        }
        bodies.forEach { body ->
            sendPacket(
                socket = socket,
                counters = counters,
                body = body,
                samples = config.samplesPerPacket,
            )
        }
    }

    private fun sendPacket(
        socket: DatagramSocket,
        counters: MicrophoneCounters,
        body: ByteArray,
        samples: Int,
    ) {
        val packet = MicrophonePacketizer.sealPacket(
            key = config.key,
            payloadType = config.payloadType,
            counters = counters,
            body = body,
            samples = samples,
        )
        try {
            socket.send(DatagramPacket(packet, packet.size, config.host, config.port))
            if (firstPacketLogged.compareAndSet(false, true)) {
                Log.i(
                    TAG,
                    "microphone first packet bytes=${packet.size} body=${body.size} " +
                        "head=${packet.copyOf(minOf(packet.size, 16)).toHexString()} " +
                        "port=${config.port}",
                )
                report("Microphone: first packet sent bytes=${packet.size} body=${body.size} port=${config.port}")
            }
        } catch (error: Exception) {
            if (running.get()) throw error
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            release()
            return
        }
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Best effort; release below is authoritative.
        }
        try {
            socket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        thread?.let { worker ->
            try {
                worker.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (worker.isAlive) worker.interrupt()
        }
        release()
    }

    @Synchronized
    private fun release() {
        running.set(false)
        val currentRecorder = recorder
        recorder = null
        try {
            currentRecorder?.release()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentSocket = socket
        socket = null
        try {
            currentSocket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentEncoder = opusEncoder
        opusEncoder = null
        currentEncoder?.close()
        val currentVoiceFocusLease = vendorVoiceFocusLease
        vendorVoiceFocusLease = null
        try {
            currentVoiceFocusLease?.close()
        } catch (_: Exception) {
            // Best effort; vendor focus integration must never break CarPlay teardown.
        }
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val MIN_READ_BYTES = 2_048
        const val CLOSE_JOIN_MILLIS = 500L
    }
}
