# Chess AI Auto Player 3.0 beta

Android ARM64 app for **offline computer-opponent chess only**. The original v1 and v2.1 branches remain unchanged. This beta has a separate package ID `io.sajad.chessauto.v3beta`, so it installs alongside earlier builds.

## Available now

- Functional dark Arabic dashboard inspired by the supplied visual reference; internal engine-vs-engine board still works.
- Full-device, **user-approved** MediaProjection capture: on Android 14+ the app explicitly requests the default display, not a cropped single application window.
- Android 14+ MediaProjection `onCapturedContentResize` updates the existing VirtualDisplay and Surface without reusing the consent token.
- Captured frames processed off the main UI thread, throttled to a practical ~8 fps; live observed FPS shown on the overlay.
- Independent board orientation from player's piece color. Separate rectangles for captured image and physical touch, with scale checks to block mismatches.
- Two distinct offline external modes: color-guided move (user taps) and automatic tap/drag (user explicitly confirms offline match).
- Pause/resume button and immediate stop in the floating overlay.
- Choice of Stockfish 19 thinking time: 180 / 350 / 750 / 1250 milliseconds.
- Visual occupancy consistency gate. If the captured board fails to match the claimed position, the app **must not** automatically move.

## Serious limitations (please read)
- **NOT** a universal visual chess-piece classifier. Only square occupancy and changed-square tracking are present. The code cannot reliably identify 12 kinds of chess pieces, so do not trust it against unfamiliar themes or arbitrary midgames.
- If you launch a match halfway through, you must supply an accurate FEN including whose turn, castling and en passant. Otherwise start a NEW standard-position match.
- For black pieces at bottom, choose the **board orientation** explicitly, separately from bot color.
- External full-screen MediaProjection may be interrupted by Android/ROM settings or black/protected windows. Try the color guidance mode first and verify every source and destination before enabling taps.
- Promotion dialogs in external apps are **not** automatically handled: the mode falls back to colored guidance for promotion.
- Color-guided/automatic modes have NOT been proven against all external Play Store chess apps and have not been tested on the user's handset.
- Production APK signing is not set up. Every GitHub debug build has an ephemeral key, so build upgrade installation across different workflow runs may fail; this beta uses a new package ID.
- Fully automatic Accessibility actions may not qualify for Google Play publication. This code is for personal offline experiments, with explicit user consent and a foreground notification.
- Offline play only: never use it against human opponents or in online games.

## Build
Branch: `chess-ai-v3-beta`.
GitHub Actions: **Chess AI Auto Player v3 Beta APK**; successful artifacts named `chess-ai-v3-beta-apk`.
Download its zip and install `app-debug.apk`. Stockfish 19 (sf_19) builds from source for ARM64 via Android NDK.

## Legal
GPL-3.0-or-later application, bundled Stockfish GPLv3 source available at https://github.com/official-stockfish/Stockfish. chesslib is Apache-2.0. No screen frames are transmitted to an external server by this application.

## Verification
GitHub compilation is a build check, *not* a hardware end-to-end test. For a release considered stable, physical device testing must validate screen capture, orientation, calibration, moves and special moves.
