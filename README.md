# Iraq Game Route X1 🇮🇶 🎮

**Android network diagnostics + opt-in, per-app WireGuard tunnel for eFootball Mobile.**

> **Status: `0.1.0-alpha` — source implementation, not yet device-certified.**
> No guaranteed lower ping, no built-in relay service, and no interaction with Konami's game binaries/protocol.

## What works in source

- Arabic RTL native screen with connectivity state and manual direct-network diagnosis.
- 5 HTTPS sample requests over a validated underlying Wi-Fi/cellular network (never labels these results as eFootball ping or UDP packet loss).
- Import standard WireGuard `.conf` from Android's file picker. **Private keys stay in volatile application memory**. Configurations are NOT bundled or saved to disk.
- User-facing Android VPN consent flow, native `GoBackend` from the official WireGuard Android tunnel library.
- Explicit `IncludedApplications` allowlist for `jp.konami.pesam` and the local app only; other apps use normal Android networking. Rejects configs that try to set conflicting application policies.
- Enforces peer endpoint and default `AllowedIPs` as a basic full-tunnel prerequisite.
- HTTPS request **through the active VPN** + WireGuard peer handshake verification before marking `ON`.
- Two consecutive tunnel-health failures trigger a best-effort VPN shutdown.
- Explicit Stop VPN, Android VPN settings link, and state-specific errors.
- Conservative route-advice model with unit tests; **not automatically connected to game measurements**.

## Real limitations

1. An actual WireGuard **server and valid client `.conf` must exist**. This project does not provide relay capacity.
2. A VPN can raise latency. Actual eFootball RTT depends on ISP routing and server placement.
3. `NET_CAPABILITY_VALIDATED`, HTTPS latency and WireGuard handshakes are **not** proof that eFootball traffic is perfect.
4. Some users enable Android **Block connections without VPN**; then automatic direct fallback can be blocked by Android policy. This app opts out of always-on, but cannot bypass such device policy.
5. Changing public IP while a match is running can terminate an online session. No mid-match auto-route switching.
6. Only one VPN can be active per Android user/profile.
7. This alpha has not passed Android CI or device tests as of this source snapshot. The Actions workflow will produce a **debug APK only if compilation succeeds**.

## How to build

Install JDK 17, Android SDK platform 36 and Build Tools 35.0.0, Gradle 8.13. The project uses Android Gradle Plugin 8.13.2 and Kotlin 2.0.21.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

Or select **Actions → Android build and unit tests** in GitHub; after a green run, download the **Iraq-Game-Route-X1-debug** artifact. Debug APK is NOT a production release and Android may warn about installing it.

## On-device usage

1. Install eFootball from its official source (`jp.konami.pesam`).
2. Open X1 and test baseline Wi-Fi/mobile connectivity.
3. Obtain a trusted WireGuard configuration for a server you administer or have permission to use.
4. Import the `.conf` file (private key is never printed in logs). Choose it from the profile selector.
5. Tap **تشغيل المسار المشفّر**, accept Android's VPN consent, verify success; the app tests actual HTTPS through the tunnel.
6. If it fails, X1 attempts to disable the VPN. Use **إيقاف VPN** or the system VPN settings as needed.
7. Compare actual eFootball match stability separately; stop the VPN if the direct path performs better.

**Do not upload real `.conf` files or private keys to GitHub.** `.gitignore` excludes them.

## Repository layout

```
app/src/main/java/.../network/    underlying network snapshots, HTTPS probes, metrics
app/src/main/java/.../vpn/        WireGuard tunnel controller, per-app config validation
app/src/main/java/.../analytics/  conservative route-scoring prototype
app/src/main/java/.../MainActivity.kt  Arabic UI and system permission flow
app/src/test/                     pure Kotlin/JUnit tests
.github/workflows/android.yml     GitHub Actions debug APK and unit tests
docs/                             architecture, test plan, references, limitations
```

## Open-source projects researched

- [WireGuard Android](https://github.com/WireGuard/wireguard-android) — **embedded official Maven library** (Apache 2.0).
- [Rethink DNS + Firewall](https://github.com/celzero/rethink-app) — design study for per-app routing and resilience (no source imported).
- [NetGuard](https://github.com/M66B/NetGuard) — design study for network state and firewall UI (GPLv3; no source imported).
- [sing-box for Android](https://github.com/SagerNet/sing-box-for-android) — alternative tunnel design research (GPLv3; no source imported).
- [LibreSpeed](https://github.com/librespeed/speedtest) — benchmarking methodology, not integrated in app.
- [Tailscale](https://github.com/tailscale/tailscale) — mesh VPN resilience reference, not integrated.

No misleading claim of merging all six VPN programs: Android has one active VPN service, and GPL dependencies require license review before source integration.

See [design](docs/ARCHITECTURE.md), [test plan](docs/TEST_PLAN.md), [source references](docs/SOURCES.md) and [security](SECURITY.md).
