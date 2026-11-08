# Architecture and threat boundaries

## Data flow

```
User taps diagnostics ---> NetworkMonitor.underlyingNetwork()
                         -> HttpsProbe network.openConnection() -> Google / Cloudflare
                         -> ProbeSummary -> UI (HTTPS timing, failures)

User imports .conf -> Config.parse -> ConfigPolicy validation and allowlist
                   -> Android VPN consent -> GoBackend.setState(UP)
                   -> HTTPS through VPN + peer handshake -> Mark ON or DOWN
                   -> Watchdog checks every 35 seconds -> WARN on first failure -> best-effort DOWN after three failures unless user enables match protection
```

## Safety invariants

1. The app starts with NO VPN and does not automatically reconnect on boot.
2. The app never activates VPN without a valid config and Android system consent.
3. `IncludedApplications` is **always regenerated** to include only this app + official eFootball package ID; user imported allowlists and denylists are rejected.
4. WireGuard's official `GoBackend` is the sole VPN service. No loopback proxy or fake connectivity switch.
5. `ON` means a WireGuard handshake **and** a successful HTTPS request through that same routing policy, not just a tun interface.
6. A failed setup attempts to shut down the tunnel. An isolated health-check failure does NOT disconnect. Repeated failures trigger best-effort shutdown **unless user has enabled match protection**, in which case the app warns without altering routes.
7. Fallback cannot defeat Android lockdown settings; warn users clearly.
8. This alpha never automatically changes between relay nodes. Match protection is explicitly user-controlled and not automatic game-session detection.

## Trust boundaries

- Imported WireGuard `.conf` is UNTRUSTED input, max 64 KiB, parsed by upstream library.
- A VPN relay operator may observe metadata and destination traffic timing. Use only trusted operators.
- This application uses the Android `VpnService` consent surface. It does not control user-defined lockdown policy.
- Diagnostic URLs are Google `generate_204` and Cloudflare `cdn-cgi/trace`. They will see a request from the user's IP; no app analytics or account identifiers are sent.
- User profile data are kept in application RAM: they survive rotation but are lost on process restart; no credential persistence.

## Currently implemented route comparison (v0.4)

User-initiated `RouteBenchmark` collects 8 samples for each route using 4 interleaved
ABBA / BAAB rounds. This avoids a fixed 5-direct-then-5-VPN scheduling bias.
The direct requests use the same Android `Network` throughout; the VPN requests
use the app's VPN route. Before and after every probe, the benchmark verifies
that the physical `Network` identity is unchanged and the tunnel remains ON/DEGRADED.
If not, it returns no comparison (never partial results). The primary URL is the same
for both paths; if either path cannot reliably reach it, both are measured using
the secondary URL. No automatic route changes occur.

`RouteDecision` uses minimum successful-sample coverage, median, P95, adjacent
HTTP transaction-time variation and HTTP failure rate. These are **not** ICMP or
UDP or the true latency to Konami game servers; results are recommendations
for pre-match user testing only.

## Extending route scoring (planned, NOT working yet)

Introduce an opt-in probe server beside each authorized relay that runs identical HTTPS probes from the Android client. Only compare comparable URLs and paths. Never equate a relay probe with Konami server RTT. Consider p50, p95, failure ratio, jitter and confidence intervals over multiple time windows. Any decision must be locked for the duration of gameplay.

A future release can implement explicit multi-node WireGuard profiles, front-end scoring, and controlled route switching *only before starting a match*. Never bundle or silently start unrelated VPN implementations simultaneously.
