# Chess AI Auto Player 3.2 — Accessibility & Capture Diagnostics

## User-visible fixes
- Independently test whether Android connected `OverlayAccessibility`.
- Button **اختبار التأشير الأسود والأخضر** draws a black outlined e2 and green outlined e4 on a demo board region without relying on MediaProjection/Stockfish.
- Button **اختبار اللمس** dispatches an actual gesture onto an in-app button. The target must change to "استقبل التطبيق اللمسة الفعلية" as proof of tap delivery.
- A diagnostic status in the Arabic UI shows whether the accessibility service is enabled and connected, and links to Android app details for restricted-setting controls when present.
- Overlay installation catches `WindowManager.addView` exceptions, displays an error, and never leaves a stuck `attached` flag.
- Sequential callback-based tap→tap improves compatibility with games that ignore two strokes in a single gesture.
- Screen sharing uses a fresh Android consent and avoids reusing a projection session; frame FPS is reported in the overlay.
- No longer jumps immediately to Home after consent; the user can see errors before opening the external chess app.
- Capture-frame heartbeat/FPS and last capture error visible inside the Arabic app, even when the overlay has stopped.
- Share a text-only diagnostic report directly from the app (no screenshot or screen data included).
- Internal chess mode records a real UCI move list and lets the user share it as text; it is NOT presented as PGN/SAN.
- En passant visual detection accounts for the third changing square before checking candidate legality.
- Dark-themed game scenes are not automatically mistaken for black/protected captures just because two sample pixels are black.
- Full-display capture resize, overlay outline markers, separate capture and touch rectangles, manual orientation choice, Stockfish 19, tap/drag, safe stop are retained.
- Separate app ID `io.sajad.chessauto.v3.repair2`: old working versions remain installed.

## Required honest limitations
The external game mode still uses **occupied versus empty squares**, not a trained 13-class piece classifier. It is only eligible to act after a strict visual check of the known starting board/FEN. It may refuse to move on various themes. A screenshot of an arbitrary midgame cannot always recover complete castling/en passant history. Promotion popup automation is not implemented: use manual hints. Debug APK build success is **not** a proof of successful on-device gesture or MediaProjection handling.

## Setup and troubleshooting
1. Install the `app-debug.apk` from the successful `chess-ai-v3-repair-apk` GitHub Actions artifact.
2. Open **Chess AI 3.2 Repair → مشاركة الشاشة**.
3. Tap **تفعيل خدمة اللمس والتأشير**, then enable service labeled **Chess AI 3.2 Repair - اللمس والتأشير** in Android Accessibility settings. If settings say `Restricted setting` for a sideloaded app on Android 13+, open the OS app details and review `Allow restricted settings` only if you trust the installed package. Do not bypass OS restrictions.
4. Tap **فحص خدمة إمكانية الوصول** and verify connected.
5. Tap **اختبار التأشير الأسود والأخضر** and verify overlay panel plus two outlined test squares. This test does not validate chess vision.
6. Tap **نفّذ لمسة اختبار** while the test target is visible; its caption changes when an actual accessibility tap is received. This is the only test that validates gesture dispatch independently.
7. Select guidance first, confirm playing ONLY against a computer, grant full-display screen sharing, open a NEW game from the standard starting position, and tap overlay **معايرة** at two corners. Verify hints before turning on auto.
8. If marker test works but chess hints do not, read the overlay's FEN/occupancy message. If FPS reads zero, inspect capture permissions. A protected window cannot be captured.
9. If the service disconnects after installing/restarting, toggle the Accessibility service off/on from Android settings.

## Source and licensing
See the repository branch `chess-ai-v3-repair`. Stockfish 19 sources: https://github.com/official-stockfish/Stockfish/tree/sf_19 (GPLv3). No screen frames are uploaded to the Internet.

**Release status:** Compile-tested debug build pending physical-device end-to-end validation, not a certified stable production release.


## About the user's 100 suggestions
The shipped platform remains **Android**, not Windows/Linux: `mss`, `PyAutoGUI`, `pynput`, `PyQt`, and `PyInstaller` are desktop Python tools and are NOT bundled into the Android APK. Android uses `MediaProjection` for consent-based screen capture, `TYPE_ACCESSIBILITY_OVERLAY` for guided hints, `AccessibilityService.dispatchGesture()` for permissioned touches, Java for app logic, and a native ARM64 build of Stockfish 19 via UCI.

Implemented now: separate capture/vision/engine/gesture functionality; human-readable diagnostics; independent overlay self-test; independent in-app touch test; board 8x8 coordinates and orientation setting; engine subprocess; strict legal move validation; capture resize; tap/drag; interruption/pause; frame stability check; conservative en passant candidate tracking; internal UCI history; user-approved offline external play. The app intentionally rejects uncertain states.

**Not implemented and not claimed**: automatic 13-class piece classifier (needs model/data/evaluation); arbitrary-midgame recovery; a universal automatic ROI locator; robust Chess960; native speech synthesis; true MultiPV evaluation bar; production persistent APK signing; PGN in SAN; full automated promotion dialogs; physical-device validation; <200ms guaranteed end-to-end latency, 60 FPS guarantee, or 99.9% guaranteed accuracy. Random "human-looking" mouse behavior and premoves are NOT provided; they add unreliability and are unnecessary for offline computer games.

## Diagnostic support
Before reporting a bug, use **فحص خدمة إمكانية الوصول**, then **اختبار التأشير الأسود والأخضر**, then **نفّذ لمسة اختبار**, and finally **فحص حالة التقاط الشاشة والإطارات** after consenting to full-display sharing. The **مشاركة تقرير فحص الصلاحيات** button exports the latest accessibility, overlay, and capture statuses as plain text. No screenshots are included in the report; inspect the text before sharing it.
