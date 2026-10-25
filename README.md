# Chess AI Auto Player — External Modes v2 (experimental)

Two user-selectable modes for games against a **computer** in external Android chess apps:
* **Automatic tap mode**: Stockfish calculates legal moves and AccessibilityService taps the piece and destination.
* **Colored guidance mode**: black border around the piece to move and green border around the target; the user performs the move.

Both use explicit Android screen capture approval (MediaProjection), manually calibrated visible board coordinates and an on-device 64-square image-difference tracker. No screenshots are sent anywhere; internet is unnecessary after installation.

**Scope / honest limitations**
- Requires **starting a new standard chess game** and calibrating the rectangle (touch its top-left then bottom-right corners) before any pieces move. Unsupported custom FEN and unsupported chess variants.
- Visual tracker is *experimental*: compares changed square center pixels against board legal move candidates. It is not a trained piece classifier or automatic universal recognition. Dynamic highlights, themes, backgrounds, reorientation, transitions, promotional selection dialogs, partial screen sharing, board movement or landscape rotation can prevent tracking. Do not assume it works with every Play Store chess app.
- Auto touch is two discrete taps. Apps requiring drag, confirmation, gestures or protected screens may not work. If the screen moves, stop and recalibrate.
- Requires manual accessibility service enablement; one MediaProjection approval per session; an ongoing visible foreground notification; external capture only in offline games. **Never use against online humans.** The app cannot independently verify opponent type; explicit user confirmation is required.
- Overlay stop button immediately ends the capture service. Disable the Accessibility permission in Settings when no longer needed.
- Internal engine-vs-engine mode from v1 is retained.
- Android 8+ min supported for built-in mode; screen sharing externally is tested only by CI compile, not on all Android versions. ARM64.
- The build must pass GitHub Actions; a successful build alone is **not a physical phone integration test**.

## APK
GitHub Actions run on branch `chess-ai-external-v2`. On successful run, find artifact `chess-ai-external-v2-apk` and unzip `app-debug.apk`.

## License
GPL-3.0-or-later. Official Stockfish 19 compiled from source in CI, GPL source https://github.com/official-stockfish/Stockfish . chesslib licensed Apache-2.0.

## Install separately from v1
Package ID: `io.sajad.chessauto.external`. It installs alongside v1 to avoid conflicts with runner-generated debug signing keys. The original working app is preserved.
