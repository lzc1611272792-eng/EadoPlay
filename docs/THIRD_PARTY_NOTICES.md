# EadoPlay credits and license notices

## Receiver

EadoPlay is a modified version of [DiPlay by shihabal3amri](https://github.com/shihabal3amri/DiPlay) and [DiPlay-Legacy-Android by programmerguohuajing](https://github.com/programmerguohuajing/DiPlay-Legacy-Android), which derive from [xcertplay by shilapi](https://github.com/shilapi/xcertplay). The upstream receiver is licensed under GNU GPL version 3; the full text is in `LICENSE` and the original README is retained in `docs/UPSTREAM-README.md`.

Upstream credits [LIVI](https://github.com/f-io/LIVI) and [Showcase](https://github.com/amineross/showcase) for protocol research. Existing source comments and attribution are preserved.

## Home and settings UI

`common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt` adapts the palette, visual arrangement and interface copy of the [DiAuto project](https://github.com/shihabal3amri/DiAuto). DiAuto's source is licensed under AGPL version 3. The UI file is marked AGPL-3.0-only; its license text is included in `docs/licenses/DiAuto-AGPL-3.0.txt`.

## CarPlay icon

The unmodified icon was obtained from Apple's developer site at:

https://developer.apple.com/assets/elements/icons/carplay/carplay-96x96_2x.png

CarPlay and the CarPlay icon are Apple Inc. marks/assets. This asset is not covered by the project's open-source code license. Its use here does not imply Apple approval or certification.

## Runtime dependencies

- AndroidX and Jetpack Compose — Android Open Source Project; Apache License 2.0.
- Kotlin standard library — JetBrains; Apache License 2.0.
- Bouncy Castle 1.79 — The Legion of the Bouncy Castle Inc.; Bouncy Castle license (MIT-style).
- JmDNS 3.6.3 — JmDNS contributors; Apache License 2.0.
- SLF4J — QOS.ch; MIT license.

Gradle dependency declarations and version catalog accompany the source. License files available in the resolved artifacts are included under `docs/licenses/dependencies/`.

## Experimental authentication data

The vehicle-test APK may include the same experimental accessory certificate/key pair described by upstream DiPlay, recovered from publicly available Carlinkit C2Air Allwinner V821 firmware during prior research. These data are not newly generated Apple-issued credentials for EadoPlay and are not relicensed as project source code. They are included only to reproduce the offline compatibility experiment; provenance, rights status, continued acceptance, and suitability for general distribution remain unresolved. The Git source archive does not contain the private key, and the separate Android APK-signing key is never distributed.

## Download website

The static site layout, CSS and generator adapt DiAuto (AGPL-3.0). The AGPL license text is included with the source.

## EadoPlay adaptation

EadoPlay removes the BYD HUD and cluster runtime code from the target build and adds compatibility work for the 2018 Changan Eado Android 4.4.2 head unit. This project name and adaptation do not imply transfer of ownership over upstream code or third-party assets.
