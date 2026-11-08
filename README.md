# Iraq Game Route X1 🇮🇶 🎮

**Android network diagnostics + opt-in, per-app WireGuard tunnel for eFootball Mobile.**

> **Status: `0.5.0-alpha` — automated pre-match route trials, not yet device-certified.**
> No guaranteed lower ping, no built-in relay service, and no interaction with Konami's game binaries/protocol.

## Self-hosted relay creation (server edition 0.6 — NEW)

The repository now includes a **tested offline provisioning toolkit** under [`server/`](server/README.md): Ubuntu install script, per-device WireGuard profile generation, peer revocation, relay health inspection, strict input checks, optional manual GitHub Actions deployment to **an existing owner-controlled VPS**, and CI verification of the scripts. The Android 0.5 APK can import these server-generated profiles without a new Android release.

**These scripts are not real hosted servers. No VPS has been rented, started or connected using this repo.** To get the one-tap route optimization to work, you must provide an actual VPS (public IPv4, SSH rights, inbound UDP enabled), provision a client profile privately on that server, import it to the app, and measure actual game quality. This kit does not offer free shared VPN exit servers or guarantee an improvement.

Start with [`server/README.md`](server/README.md), then run the safe deployment checks under **Actions → Relay scripts checks**. Actual server deployment can be initiated through **Actions → Deploy IGRX1 relay to existing VPS** after deliberately setting encrypted GitHub SSH secrets and verifying its host key.

---

## What works in source

- Arabic RTL native screen with connectivity state and manual direct-network diagnosis.
- 5 HTTPS sample requests over a validated underlying Wi-Fi/cellular network (never labels these results as eFootball ping or UDP packet loss).
- Import standard WireGuard `.conf` from Android's file picker. **Private keys stay in volatile application memory**. Configurations are NOT bundled or saved to disk.
- User-facing Android VPN consent flow, native `GoBackend` from the official WireGuard Android tunnel library.
- Explicit `IncludedApplications` allowlist for `jp.konami.pesam` and the local app only; other apps use normal Android networking. Rejects configs that try to set conflicting application policies.
- Enforces peer endpoint and default `AllowedIPs` as a basic full-tunnel prerequisite.
- HTTPS request **through the active VPN** + WireGuard peer handshake verification before marking `ON`.
- A conservative health policy: a single HTTP failure cannot disconnect an established tunnel. Three consecutive checks (35 seconds apart) trigger a best-effort shutdown only when match protection is **disabled**. Match protection defaults to **ON**, warns without switching routes, and does not repair a failed network.
- Requires a **recent** WireGuard handshake; an old historical handshake can no longer make a dead tunnel look healthy.
- Detects an already-active VPN and refuses to silently replace another application's VPN service.
- Persists the pending VPN consent choice across Activity recreation, but never stores private keys on disk.
- Rotation-safe observer cleanup so the previous screen cannot detach the new screen's tunnel status observer.
- Explicit Stop VPN, Android VPN settings link, and state-specific errors.
- Interleaved **ABBA/BAAB** direct vs VPN HTTPS benchmark (8 samples per path, identical target, aborts if Wi-Fi/cellular path or VPN changes) and conservative route recommendations. These are **not in-game RTT values**.
- **NEW v0.5**: explicit one-tap **automatic PRE-MATCH route optimizer**. Tests up to three already-imported, trusted WireGuard profiles one at a time; checks a fresh handshake and tunneled HTTPS, benchmarks each relay against the same underlying physical network, chooses only a statistically favorable candidate, then repeats its benchmark. Otherwise it turns the VPN OFF and uses direct networking (unless blocked by Android's VPN lockdown policy). It does not scan for free public relays, intercept eFootball traffic, or change a route once play starts.
- Automatic trials can temporarily change connectivity while they run. Start them **before launching a match**. The tool cannot independently determine whether a match is already running, and its HTTPS scoring cannot guarantee better in-game gameplay. The Stop button cancels the running optimizer and queues tunnel shutdown.
- Imported profiles persist across activity recreation (e.g. screen rotation) but **not application process restart**; no config secrets are written to disk.

## Real limitations

1. An actual WireGuard **server and valid client `.conf` must exist**. This project does not provide relay capacity.
2. A VPN can raise latency. Actual eFootball RTT depends on ISP routing and server placement.
3. `NET_CAPABILITY_VALIDATED`, HTTPS latency and WireGuard handshakes are **not** proof that eFootball traffic is perfect.
4. Some users enable Android **Block connections without VPN**; then automatic direct fallback can be blocked by Android policy. This app opts out of always-on, but cannot bypass such device policy.
5. Changing public IP while a match is running can terminate an online session. No mid-match auto-route switching.
6. Only one VPN can be active per Android user/profile.
7. v0.4 was built as a debug APK in GitHub Actions; **v0.5 requires its own CI build and physical-device validation**. Unit tests alone are not production certification.

## How to build

Install JDK 17, Android SDK platform 36 and Build Tools 35.0.0, Gradle 8.13. The project uses Android Gradle Plugin 8.13.2 and Kotlin 2.4.21.

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
6. Before starting a match, tap **⚡ اختيار أفضل مسار تلقائياً قبل المباراة** to test up to three imported profiles. This temporarily brings VPN connections up/down, so do not run the optimizer during a match. If no VPN beats direct HTTPS decisively, direct stays selected.
7. Alternatively use **مقارنة المسارين** to compare HTTPS timing on direct and VPN routes manually. **حماية المباراة** is ON by default and prevents automatic watchdog shutdown during gameplay.
8. If the tunnel fails, X1 attempts to disable the VPN. Use **إيقاف VPN** or Android VPN settings as needed.
9. Compare actual eFootball match stability separately; stop the VPN if the direct path performs better.

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

## v0.4 verification (2026-11-08)

- 25 pure Kotlin JUnit-style checks compiled and passed locally, with Java stub JUnit assertions; **not** an Android app build.
- AGP 8.13.2, Gradle 8.13, Kotlin 2.4.21 and WireGuard tunnel 1.0.20260102 were matched to published compatibility and artifact sources (Android build still not run locally).
- CI and real Android hardware results are still required before describing the app as production-ready.
- No real VPS, tunnel secret, or Konami server access is bundled. See [relay prerequisites](docs/RELAY_SETUP.md).

## v0.4 connection-safe comparison

- ABBA/BAAB sampling: direct → VPN → VPN → direct, alternating reverse order on the next round. Eight transactions per path reduce timing bias from a rapidly changing ISP; this is not real UDP jitter or game ping.
- All 16 samples use one HTTPS endpoint. If one path cannot reliably reach it, the benchmark repeats with the secondary endpoint **for both paths**.
- A change in underlying Wi-Fi/cellular network or loss of the active VPN invalidates and discards the full comparison.
- Requires at least 7/8 successful transactions per route, 15% AND 8ms lower median, non-worse P95 and jitter/failure ratio to classify the relay as a candidate. This is a conservative heuristic, not statistical proof and **never auto-switches**.
- Refuses to start a replacement tunnel if the previous backend still reports UP after an uncertain failure.
- CI and real-device testing are mandatory prior to advertising any latency benefit; sample endpoints are Google/Cloudflare, not Konami.

## v0.5 automatic mode: design safeguards

- Opt-in user action and normal Android VPN consent are required. No always-on auto-start or hidden switching.
- The best candidate is selected from **at most 3 locally imported, explicitly authorized** profiles. No shared public-server list is shipped.
- Each candidate must pass a peer handshake check and actual HTTPS reachability through its tunnel; every pair of route measurements uses the same HTTP endpoint.
- A candidate is enabled only when it is consistently better in **two separate benchmark passes**. If not, the VPN is turned off. This is still a heuristic for general internet access, not a measurement of eFootball game RTT.
- Any observed change of the physical network, VPN failure, or user Stop request cancels the trial and attempts clean shutdown. Fail-open depends on Android lockdown settings; do not imply a 100% no-disconnect guarantee.
- Watchdog's match protection remains enabled by default once a successful candidate is selected, so the app does not intentionally rotate the route during play.
- Pure Kotlin local unit suite: 30/30 tests passed using a lightweight JUnit API stub, **not** an Android emulator/device test. Full GitHub Actions results and actual gaming impact are separate acceptance gates.

## Live VPS connectivity checks (server v0.7)

The relay server code now includes `server/verify-live-client.sh`, which requires a recent handset handshake **and** encrypted RX/TX traffic on a real server. The health check also verifies local IPv4 masquerading. The GitHub repository does **not** supply hosting, a public IP, secret client keys, or a live network. See [live deployment checklist](docs/LIVE_DEPLOYMENT.md).
