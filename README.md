# Chess AI 3.3 — Functional FEN Guidance Fallback (Android ARM64)

## Why this build exists
The user reported **all** external chess features as nonfunctional. In earlier versions both guidance and automatic gestures depended on a fragile visual occupancy gate. This build provides a **genuinely separate Stockfish analysis and overlay guidance path** requiring NO MediaProjection, so capture and chess inference are no longer prerequisites for seeing a calculated chess hint.

### New working code paths (compile verification still required)
1. Open external screen, enable Android Accessibility as usual; make sure its service is actually connected.
2. Confirm game is only versus offline CPU.
3. Select the side whose turn it is, board orientation and thinking time.
4. Enter exact FEN for the current position, or leave blank to use the standard starting position.
5. Tap **♟ إرشاد Stockfish من FEN — بدون مشاركة**.
6. Open an offline chess game in another app, tap **معايرة** in the floating bar, and select full-board top-left/bottom-right.
7. Stockfish 19 computes a *legal candidate* from the supplied FEN, then the accessibility overlay attempts to draw the black source-square outline and green destination-square outline.
8. This FEN is supplied by the user. The screenshot is **NOT** checked in this mode. Check source/target visually and move the piece yourself. For the next position, end the session, return to this app, update the FEN and start another hint.

**The manual FEN fallback cannot play a match automatically, detect a move, or validate a user's FEN against the external board. Automatic external moves are explicitly DISABLED in this mode.** This constraint prevents blind gestures when camera vision is not actually working.

## Other improvements retained from v3.2
- Real test for Android Accessibility connection and display of latest overlay error;
- Explicit overlay test and real in-app test target for accessibility touch dispatch;
- Screen-capture health report, measured FPS, exportable text diagnostics (no screenshots);
- Shared screen-size and gesture mapping with separate calibration and capture coordinates;
- Capture session resize behavior, full-display request for Android 14+, clear stop controls;
- User-selectable orientation, tap/drag methods, Stockfish time;
- Internal engine-vs-engine mode, exportable *UCI* move list;
- En passant third-square change candidate and constrained move verification.
- Package ID `io.sajad.chessauto.v33fallback` is independent of prior experimental builds.

## What this DOES NOT do
It does **not** implement a trained chess-piece classifier, reliable live recognition of arbitrary chess boards, general 3D board support, Android-wide automation in every chess app, auto promotion, or a validated hands-free game versus a computer.

**Build compilation success is not on-device proof.** The user will need to run the overlay self-test and new manual-FEN hint flow on their physical Android phone. Avoid promising full screen reading or gestures until actual field tests pass.

## Technical notes
Android native: Java, Android MediaProjection, TYPE_ACCESSIBILITY_OVERLAY, AccessibilityService.dispatchGesture, Stockfish 19 UCI process. The desktop-specific suggestions for mss, PyQt, PyAutoGUI and PyInstaller do not belong to an Android APK. No screenshots are uploaded to servers. Offline CPU games only; not suitable for play against humans.

## APK
GitHub Actions on `chess-ai-v3-functional-fallback`, artifact `chess-ai-v3-functional-fallback-apk`, APK `app-debug.apk`. Requires ARM64 Android 8+, subject to manufacturer Accessibility restrictions. GPLv3 source and dependencies retained.
