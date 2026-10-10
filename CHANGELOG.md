# EadoPlay 0.2.7-4.4-eado-r28 — 2026-10-10

- Add separate 20–100% software-volume controls for CarPlay music and navigation guidance. Gain is applied to decoded PCM so it works on the target Android 4.4 audio HAL while preserving CarPlay navigation ducking.
- Add the Changan Coagent microphone-focus path and an Android 4.4 capture fallback that records at 16 kHz and resamples to the CarPlay-negotiated rate. System calls and incoming WeChat calls were verified with the phone locked.
- Add microphone negotiation, recorder, encoder and first-packet diagnostics without recording call audio or protocol payloads.
- Restore the r17 wireless handoff behavior by disconnecting the factory HFP/A2DP/AVRCP profiles only after the authenticated Wi-Fi CarPlay tunnel is active; pairing remains intact and profiles are restored when CarPlay closes.
- Monitor Bluetooth adapter/profile reconnection after an ignition cycle and reapply isolation after a short settling delay. Playback-triggered checking is event-driven and limited to at most once every 30 seconds.
- Add elapsed-time diagnostics for the wireless connection stages. Runtime logs remain bounded to one 512 KiB current file plus seven archives (about 4 MiB maximum).
- Keep wired USB marked experimental: the target firmware still detects the iPhone but does not complete the Android USB Host permission step.
- Known limitation: an outgoing WeChat call started from CarPlay while the iPhone is locked may remain silent until the phone is woken. System calls in both directions and incoming WeChat calls were verified.
- Rebuild the release artifact from a clean tree, removing about 4.24 MiB of accidental ZIP padding. The standalone APK is 9.711 MiB; no runtime feature was removed.

Intermediate r23–r27 packages were vehicle-test iterations and are included cumulatively in r28 rather than published as separate releases.

# EadoPlay 0.2.7-4.4-eado-r22 — 2026-10-09

- Route CarPlay microphone ownership through the factory `coagent.receiver.requestMicWithBt` Binder API, which is the only path the iFlytek middleware accepts on this head unit.
- Request the microphone before Android `AudioRecord` starts and release it through the same factory relay after teardown; the vendor service handles delayed restoration and Bluetooth-call/TBOX conflicts.
- Replace the ineffective direct `coagent.voice` call discovered by locked-screen vehicle testing, without stopping or disabling any factory process.

# EadoPlay 0.2.7-4.4-eado-r21 — 2026-10-09

- Release the stock Coagent/iFlytek voice focus through its vendor Binder API while a CarPlay microphone uplink is active, then restore it after the recorder is released.
- Avoid force-stopping or disabling the factory speech service; unsupported firmware falls back to the previous microphone behavior with explicit diagnostics.
- Confirm through locked/unlocked call traces that the iPhone negotiation succeeds and the Android 4.4 recorder conflict, rather than CarPlay authentication or Opus, prevents microphone packets while locked.

# EadoPlay 0.2.7-4.4-eado-r20 — 2026-10-09

- Add 20%, 30%, and 40% software-volume choices for head units where 50% remains too loud.
- Persist microphone negotiation, recorder startup, failure, and first-packet diagnostics to distinguish iPhone/WeChat routing from vehicle microphone capture failures.
- Leave navigation buffering unchanged; current vehicle logs attribute the reported gap to wireless packet arrival rather than PCM volume processing.

# EadoPlay 0.2.7-4.4-eado-r19 — 2026-10-09

- Apply music/navigation volume directly to decoded PCM so the selected level remains effective on Android 4.4 vehicle audio drivers that ignore `AudioTrack` software volume.
- Log the effective output gain when each CarPlay audio track is created for easier vehicle-side verification.

# EadoPlay 0.2.7-4.4-eado-r18 — 2026-10-09

- Add separate 50–100% software volume controls for CarPlay music and navigation guidance while preserving CarPlay ducking.
- Keep the Bluetooth HFP call profile available after wireless handoff so locked-screen VoIP can fall back to the vehicle call path.
- Continue isolating A2DP/AVRCP to prevent the stock Bluetooth player from pausing CarPlay music.
- Detect Bluetooth adapter/media reconnection after an ignition cycle and automatically reapply media-profile isolation without restarting CarPlay.
- Add elapsed-time diagnostics for the major wireless connection stages without shortening authentication or protocol timeouts.

# EadoPlay 0.2.7-4.4-eado-r17 — 2026-10-08

- Adapt the application identity, interface, and defaults for the 2018 Changan Eado Android 4.4.2 head unit.
- Remove BYD HUD, cluster, and vehicle-specific background integrations not used by the target car.
- Add detailed connection-stage diagnostics and Android 4.4 compatibility paths for hotspot, Bluetooth, audio, and video.
- Add Changan steering-wheel media controls and system-call answer/hang-up handling.
- Improve playback-state synchronization, audio focus behavior, navigation ducking, and vendor mute-state bridging.
- Add legacy Opus support and video queue recovery for the target i.MX6-class platform.
- Improve wireless handoff, IPv4 discovery, and mDNS fallback behavior.
- Add experimental SurfaceView/TextureView, frame-rate, resolution, and audio-routing settings.
- Wireless CarPlay is vehicle-tested; wired USB remains blocked at the target firmware's USB Host permission stage.

# DiPlay 0.2.7 — 2026-09-29

- App interface in English, Simplified Chinese, Arabic, Russian and Spanish; synchronized Android app-language settings.
- Steering-wheel media controls and long-press Siri on supported BYD firmware while CarPlay is on screen.
- Dashboard display choices: map, turn card, or both; corrected dashboard keyframe recovery.
- Optional ADB feature on supported DiLink 5.0: pause the dashboard map stream when its display mode hides the map.
- Optional ADB battery reporting for Apple Maps, with warning threshold, charging-connector selection and a checked reconnect action.
- Audio playback reliability fixes and clearer dashboard settings.
- Clarify the BYD-only support scope on the README and all five website editions.

# 0.2.0 — BYD navigation and connection improvements

- Standalone windshield HUD arrows, distance and street names on the verified DiLink5.1 firmware; no ADB, root or computer helper.
- Retain contributor cluster/SOME-IP navigation, route parsing, BYD CarPlay icon and display-size presets.
- Fix Car hotspot startup by using scoped IPv6 when available and binding discovery/probing to the AP interface. Physically confirmed on the development car.
- Drain asynchronously decoded audio during packet gaps and rebuild the music buffer after starvation. Wi-Fi Direct is much better in the user retest; occasional audio cutouts remain for a later version.
- Preserve bounded music-buffer choices, USB read improvements and decoder recovery; fix USB request/close races and keep vendor output outside phone callbacks.
- Save audio/video/receive timing and discovery diagnostics without road names or protocol payloads.
- HUD cleanup on normal end/disconnect/off/stale input; interrupted sessions recover on the next app launch. Force-stop may leave guidance visible until reopening.
- Thanks to @romanchukg-cloud and @georgiyrr for PR #3 and vehicle testing.

# 0.1.0 release restored — 2026-09-25

- Rebuilt and signed the APK locally with explicitly supplied runtime authentication assets.
- Restored release downloads; no app behavior or version-code change from 0.1.0.
- Accessory identity remains in the APK only. No credential files enter Git or the source archive.
- Retained generated test identities and public-source credential checks.
- Source/CI builds omit runtime identity assets by default; local packaging requires an explicit external directory.

# Source reset — 2026-09-25

- Withdrew the 0.1.0 APK and removed its release tag.
- Reset the public branch after preserving restricted local incident records.
- Removed static synthetic test private keys; generate test identities at runtime.
- Removed automatic private-asset packaging and disabled the old release build script.
- Added a build guard rejecting credential asset files.
- Replaced the download site with a five-language suspension notice.

The APK was subsequently rebuilt and restored as described above. Existing copies cannot be recalled by a Git history reset.
