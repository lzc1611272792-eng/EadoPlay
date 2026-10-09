# EadoPlay r17

EadoPlay r17 is the first public source snapshot focused on the 2018 Changan Eado factory Android 4.4.2 head unit.

## Highlights

- Vehicle-tested wireless CarPlay with navigation, touch, and media audio.
- Changan steering-wheel volume, playback, track, and system-call controls.
- Android 4.4 compatibility work for audio, video, networking, codecs, and background execution.
- Detailed connection-stage diagnostics and configurable rendering/performance options.
- BYD HUD, cluster, and unused vehicle integrations removed from the target project.

## Known limitation

Wired CarPlay is experimental. The target vehicle recognizes the iPhone USB identity, but the current firmware does not complete the Android USB Host permission step. Wireless mode is the verified path for this release.

## APK authentication notice

The attached standalone test APK contains the experimental accessory identity described in `SECURITY.md` and `docs/THIRD_PARTY_NOTICES.md`. It is not Apple-certified, can be extracted from the APK, and may stop being accepted by a future iOS release. The Git source tree does not contain that identity, Android signing keys, or real pairing records.

## Installation

The Changan head unit may reject normal APK installation. Follow `docs/长安逸动2018车机ADB安装APK操作手册.md` only on the intended head unit and while the vehicle is safely parked.

## Artifact

- Package: `com.eadoplay.carplay`
- Version: `0.2.7-4.4-eado-r17`
- Minimum Android version: Android 4.4 / API 19
- SHA-256: `40bc987153c9fba3b1d70932b852d62a540f2634719caf20d4b5676867fb7574`
- APK signature: Android debug/test certificate; future updates must use a compatible signature or be installed through the documented engineering-ADB replacement procedure.
