# EadoPlay

EadoPlay is an independent CarPlay client adapted for the **2018 Changan Eado factory head unit running Android 4.4.2**. It is derived from [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay), [DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android), and the upstream [xcertplay](https://github.com/shilapi/xcertplay) project.

Current vehicle-tested build: `0.2.7-4.4-eado-r28`. See [README.zh-CN.md](README.zh-CN.md) for the full Chinese documentation.

## Status

- Wireless CarPlay works on the target head unit, including navigation, media audio and touch input.
- Steering-wheel volume, play/pause, previous/next, and system-call answer/hang-up controls are adapted.
- Music and navigation have independent 20–100% software-volume controls with CarPlay navigation ducking preserved.
- The authenticated wireless handoff isolates the factory HFP/A2DP/AVRCP profiles to prevent reverse pause commands, monitors ignition-cycle Bluetooth reconnection, and restores the profiles when CarPlay closes.
- Changan Coagent microphone focus and a legacy 16 kHz capture/resampling path improve Android 4.4 call compatibility. Locked system calls in both directions and incoming WeChat calls are vehicle-tested.
- Compatibility paths are included for Android 4.4.2 audio, video, networking and background-service behavior.
- Experimental controls include 30/60 FPS, resolution scaling and SurfaceView/TextureView selection.
- The connection page exposes detailed stages and failure points for on-vehicle diagnosis.
- Wired CarPlay is implemented but is not yet end-to-end validated on the target vehicle; the current blocker is the Android USB Host permission stage.
- Known limitation: an outgoing WeChat call started from CarPlay while the iPhone is locked may remain silent until the phone is woken.

## Source builds and runtime authentication

The Git tree does not contain accessory certificates or private keys, Android release-signing keys, or real iPhone pairing records. An ordinary source build is intended for development and review and does not contain the runtime identity needed for standalone CarPlay accessory authentication.

Following the public upstream release model, the vehicle-test APK attached to a Release may be built with an experimental accessory identity recovered during research of publicly available Carlinkit firmware. Any APK recipient can extract that identity. It is not a newly issued Apple/MFi identity for EadoPlay, and continued acceptance by future iOS versions is not guaranteed. See [SECURITY.md](SECURITY.md), [docs/BUILD.md](docs/BUILD.md), and [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

Build the source-only debug APK with:

```powershell
.\gradlew.bat :mobile:assembleDebug
```

A standalone test build requires externally provisioned authentication assets selected through `DIPLAY_AUTH_ASSETS_DIR`. Those files must remain outside Git.

## Installation

The target vehicle firmware rejects ordinary third-party APK installation. Vehicle testing therefore uses its engineering ADB access to place the APK under `/data/app`. This procedure is device-specific and should not be assumed safe or available on other head units. See the Chinese [ADB installation guide](docs/长安逸动2018车机ADB安装APK操作手册.md).

## Disclaimer

- This is independent community research, not an official product of or an endorsement by Apple, Changan Automobile, Carlinkit, or the upstream maintainers.
- Apple, CarPlay, Changan, vehicle model names, and related marks or assets belong to their respective owners and are used only to identify compatibility targets.
- No warranty is made regarding the provenance, rights status, continued validity, or general distribution suitability of experimental authentication data.
- The software is provided as-is, without warranties of merchantability, fitness, reliability, or universal compatibility. Users assume all risk arising from installation, use, and modification of vehicle systems.
- Do not represent this project as an official or certified product. Redistributed modifications must retain all applicable open-source licenses, attribution, copyright, and third-party notices.
- Work on the head unit only while the vehicle is safely parked.

## License and credits

The receiver derives from xcertplay/DiPlay under GNU GPL v3. Portions of the interface derive from DiAuto under AGPL v3. See [LICENSE](LICENSE), `docs/licenses`, and [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

Thanks to the xcertplay, DiPlay, DiPlay-Legacy-Android, DiAuto, LIVI, Showcase, and related open-source contributors. EadoPlay branding and Changan-specific adaptation do not alter upstream ownership of the original work.
