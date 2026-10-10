# EadoPlay r28

EadoPlay r28 is the current vehicle-tested build for the 2018 Changan Eado factory Android 4.4.2 head unit. It includes the stable r17 feature set plus the audio, call-microphone and ignition-cycle Bluetooth fixes validated during r18–r28 testing.

## User-facing changes

- Separate 20–100% software-volume controls for music and navigation. The target Android 4.4 audio driver ignores normal `AudioTrack` volume changes, so r28 applies gain directly to decoded PCM while retaining CarPlay navigation ducking.
- Changan Coagent microphone-focus integration and a legacy 16 kHz capture/resampling path for the factory audio stack.
- Wireless handoff disconnects the factory HFP/A2DP/AVRCP profiles after the authenticated Wi-Fi tunnel is active, preventing the stock Bluetooth player from pausing CarPlay music.
- Bluetooth reconnection after an ignition cycle is detected and isolated again after a short settling delay. Playback-triggered checking is event-driven and limited to at most once every 30 seconds.
- More precise wireless-stage and microphone diagnostics. Logs are bounded to about 4 MiB total and do not record call audio or protocol payloads.

## Vehicle-test status

- Wireless navigation, touch, music and steering-wheel controls: verified.
- System calls, incoming and outgoing, with the iPhone locked: verified.
- Incoming WeChat calls with the iPhone locked: verified.
- Outgoing WeChat call started from CarPlay while the phone is locked: known limitation; the call may remain silent until the phone is woken.
- Wired USB: experimental; the head unit detects the iPhone but its firmware does not complete the Android USB Host permission step.

## Installation and upgrade

Install as an update over the existing EadoPlay package to preserve settings. The target Changan firmware may require the engineering-ADB replacement procedure documented in `docs/长安逸动2018车机ADB安装APK操作手册.md`.

The attached standalone APK contains the experimental accessory identity described in `SECURITY.md` and `docs/THIRD_PARTY_NOTICES.md`. It is not Apple-certified, is extractable by recipients and may stop working with a future iOS release. The source tree contains no accessory private key, Android signing key or real pairing record.

## Artifact

- File: `EadoPlay-r28.apk`
- Package: `com.eadoplay.carplay`
- Version: `0.2.7-4.4-eado-r28` (`versionCode 38`)
- Minimum Android version: Android 4.4 / API 19
- Size: 10,182,994 bytes (9.711 MiB)
- SHA-256: `9ca16724a2606021325f728c74565365ec751afecd0d35cf230e8b9168f30abd`
- Signature: Android debug/test certificate, v1 and v2 verified. Updating a differently signed installation may require the documented engineering-ADB replacement procedure.
