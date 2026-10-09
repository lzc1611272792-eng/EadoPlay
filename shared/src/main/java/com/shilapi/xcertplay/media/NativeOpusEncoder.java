package com.shilapi.xcertplay.media;

import android.util.Log;

import java.io.Closeable;

/** JNI-backed 48 kHz mono Opus encoder for legacy Android microphone uplink. */
public final class NativeOpusEncoder implements Closeable {
    private static final String TAG = "xcertplay-usb";
    private static final int OPUS_APPLICATION_VOIP = 2048;
    private static volatile boolean probed;
    private static boolean available;

    private long handle;
    private boolean closed;

    private NativeOpusEncoder(long handle) {
        this.handle = handle;
    }

    public static boolean isAvailable() {
        if (!probed) {
            synchronized (NativeOpusEncoder.class) {
                if (!probed) {
                    boolean ready = false;
                    try {
                        System.loadLibrary("xcertplay_opus");
                        long probe = nativeCreate(48_000, 1, OPUS_APPLICATION_VOIP, 48_000);
                        if (probe != 0) {
                            nativeDestroy(probe);
                            ready = true;
                        }
                    } catch (Throwable error) {
                        Log.w(TAG, "native Opus encoder unavailable", error);
                    }
                    available = ready;
                    probed = true;
                }
            }
        }
        return available;
    }

    public static NativeOpusEncoder create(int bitrate) {
        if (!isAvailable()) return null;
        try {
            long handle = nativeCreate(48_000, 1, OPUS_APPLICATION_VOIP, bitrate);
            if (handle == 0) {
                Log.w(TAG, "native Opus encoder create failed bitrate=" + bitrate);
                return null;
            }
            Log.i(TAG, "native Opus encoder started bitrate=" + bitrate);
            return new NativeOpusEncoder(handle);
        } catch (Throwable error) {
            Log.w(TAG, "native Opus encoder create error", error);
            return null;
        }
    }

    public byte[] encode(byte[] pcm) {
        if (closed || handle == 0 || pcm.length == 0) return null;
        try {
            return nativeEncode(handle, pcm, pcm.length / 2);
        } catch (Throwable error) {
            Log.w(TAG, "native Opus encode failed", error);
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

    private static native long nativeCreate(int sampleRate, int channels, int application, int bitrate);
    private static native byte[] nativeEncode(long handle, byte[] pcm, int frameSamples);
    private static native void nativeDestroy(long handle);
}
