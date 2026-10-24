# Chess AI Auto Player ♟️

Android app: **fully automatic engine-versus-engine chess**. The strong bot uses **Stockfish 19** to play every move for the player, while a weaker Stockfish computer plays the opposing color. No server, internet connection or root permission is needed **at runtime**.

## Current scope
- ✅ Autonomous bot playing WHITE or BLACK against an internal computer opponent.
- ✅ Real Stockfish 19 (compiled for **arm64-v8a** during GitHub Actions).
- ✅ FEN position loading, legal moves validated by chesslib, visible board, Start/Pause/New Game.
- ✅ Opponent difficulty (3 Elo targets). Note Stockfish Elo calibration is approximate.
- ❌ This release **does not** control other chess applications or automatically recognize external screens. It is NOT a general-purpose AccessibilityService bot. We explicitly avoid claiming that unimplemented feature.
- ❌ APK has not been tested on a physical device until a human installs it. GitHub Actions build result should be verified.

## Build APK on GitHub
Go to **Actions** > **Chess AI Android APK** > the successful run > **Artifacts** > `chess-ai-auto-player-apk`. Download its ZIP and extract `app-debug.apk`, then install on an ARM64 Android phone. The workflow triggers on pushes to `chess-ai-auto-player`.

Requirements: Android 8+ (API26), 64-bit ARM CPU. Android SDK/NDK are installed by CI. Official Stockfish source tag: `sf_19`.

### How it works
`StockfishEngine` runs the compiled UCI executable from Android nativeLibraryDir. `MainActivity` alternates engine requests between bot (full strength, more thinking time) and computer (limited strength) and verifies each move with chesslib. `ChessBoardView` renders the actual position.

### Build from source locally
With Android SDK and Gradle 8.11.1, first cross-compile Stockfish with Android NDK 27c (`ARCH=armv8 COMP=ndk`), copy to `app/src/main/jniLibs/arm64-v8a/libstockfish.so`, then run `gradle :app:assembleDebug`.

## Licenses
The app is GPL-3.0-or-later; Stockfish is GPL-3.0, and its GPL COPYING is packaged in the APK under `assets/Stockfish-GPLv3.txt`. Chesslib: Apache 2.0. Stockfish source: https://github.com/official-stockfish/Stockfish — chesslib: https://github.com/bhlangonijr/chesslib .

**Safety of claims:** A completed Gradle build verifies APK compilation; actual gameplay and execution on a specific phone must still be verified there.
