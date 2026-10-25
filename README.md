# Chess AI Auto Player v3.0 ScreenFix (experimental)

## What actually changed
- Screen-sharing requests **full-display** MediaProjection on Android 14+, avoiding one-app coordinate-offset errors for typical devices; user explicitly grants every session.
- Screen capture is handled on a background HandlerThread (no full-frame vision on UI thread), throttled to roughly 8 fps.
- Captured-content resize callback resizes VirtualDisplay and ImageReader, invalidates board calibration, and pauses guidance/taps until the board is recalibrated.
- Separates screenshot-pixel board rectangle from touch-screen board rectangle with an aspect-ratio safety check.
- Screen overlay now uses outlines only, to avoid hiding the piece or destination.
- Existing Stockfish 19 engine, legal move validation and internal computer-vs-computer mode retained.
- Separate application ID installs beside v1 and v2 to avoid per-build debug signing conflicts.

## Features NOT YET implemented or validated
- A trained **13-class piece classifier** (empty + twelve piece types): absent. Occupancy checks cannot reconstruct arbitrary mid-game positions. Supply exact FEN for mid-game, otherwise start a new standard match.
- Drag gestures and promotion popup interaction across all external chess apps.
- True independent orientation auto-detection. Orientation currently follows selected color.
- Physical-device tests for Chess.com and other Play Store chess apps. CI compilation is NOT functional proof.
- Full visual likeness to the previous luxury mockup: v3 ScreenFix prioritizes capture fixes; UI polish is incremental.

## Test safely against a computer
1. Install `chess-ai-v3-screenfix-apk` from the successful GitHub Actions artifact, unzip `app-debug.apk`.
2. Start **Chess AI 3 Screen Fix** and choose *colored guidance first*, set color, explicitly confirm offline computer-only play.
3. Enable accessibility, request full display share, open a brand new computer match, and tap "تحديد الرقعة" in the top floating panel. Select entire board top-left and bottom-right corners.
4. If the screen changes size, recalibrate. If board occupancy does not match, the service intentionally refuses suggestions instead of guessing.
5. Only use touch-auto after visually confirming that colored guidance identifies correct source and destination repeatedly.

Stockfish 19 is compiled for ARM64 from official source in GitHub Actions. License GPL-3.0-or-later, chesslib Apache 2.0. No screenshots are uploaded.
