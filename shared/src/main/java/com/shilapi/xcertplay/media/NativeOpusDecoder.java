package com.shilapi.xcertplay.media;

import android.util.Log;

import java.io.Closeable;

/**
 * Small JNI-backed Opus decoder used on Android versions that predate the platform Opus codec.
 *
 * The native library is probed before it is advertised to the phone, so a missing or incompatible
 * ABI safely falls back to MediaCodec/PCM negotiation instead of crashing the CarPlay session.
 */
public final class NativeOpusDecoder implements Closeable {
    private static final String TAG = "xcertplay-usb";
    private static volatile boolean probed;
    private static boolean available;

    private long handle;
    private final int channels;
    private boolean closed;

    private NativeOpusDecoder(long handle, int channels) {
        this.handle = handle;
        this.channels = channels;
    }

    public static boolean isAvailable() {
        if (!probed) {
            synchronized (NativeOpusDecoder.class) {
                if (!probed) {
                    boolean ready = false;
                    try {
                        System.loadLibrary("xcertplay_opus");
                        long probe = nativeCreate(48_000, 1);
                        if (probe != 0) {
                            nativeDestroy(probe);
                            ready = true;
                        }
                    } catch (Throwable error) {
                        Log.w(TAG, "native Opus decoder unavailable", error);
                    }
                    available = ready;
                    probed = true;
                }
            }
        }
        return available;
    }

    public static NativeOpusDecoder create(int sampleRate, int channels) {
        if (!isAvailable()) return null;
        try {
            long handle = nativeCreate(sampleRate, channels);
            if (handle == 0) {
                Log.w(TAG, "native Opus decoder create failed rate=" + sampleRate + " channels=" + channels);
                return null;
            }
            Log.i(TAG, "native Opus decoder started rate=" + sampleRate + " channels=" + channels);
            return new NativeOpusDecoder(handle, channels);
        } catch (Throwable error) {
            Log.w(TAG, "native Opus decoder create error", error);
            return null;
        }
    }

    public byte[] decode(byte[] packet, int maxFrameSamples) {
        if (closed || handle == 0 || packet.length == 0) return null;
        try {
            return nativeDecode(handle, packet, maxFrameSamples, channels);
        } catch (Throwable error) {
            Log.w(TAG, "native Opus decode failed", error);
            return null;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        long current = handle;
        handle = 0;
        if (current != 0) nativeDestroy(current);
    }

    private static native long nativeCreate(int sampleRate, int channels);
    private static native byte[] nativeDecode(long handle, byte[] packet, int maxFrameSamples, int channels);
    private static native void nativeDestroy(long handle);
}
