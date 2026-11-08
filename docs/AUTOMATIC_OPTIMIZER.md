# Auto Route Optimizer (v0.5 Alpha)

## Preconditions
1. Android VPN user consent.
2. eFootball officially installed (`jp.konami.pesam`).
3. One to three valid, user-provided WireGuard configurations to **trusted relays**.
4. Physical Wi-Fi/cellular network with validated internet.
5. No other VPN running; optimization only allowed when this app is OFF/FAILED.
6. Start only **before** a game session. We cannot reliably read the game’s matchmaking state without modifying it.

## Pre-match algorithm

- Serialize all VPN transitions on one executor; one tunnel is active at a time.
- For each of up to 3 profiles: establish its tunnel; verify HTTPS through VPN and fresh peer handshake.
- Collect interleaved ABBA/BAAB measurements against a single HTTPS endpoint, using the same physical underlay: 8 direct and 8 VPN samples.
- Cancel and safely shut down if underlay changes, tunnel stops, or user presses Stop.
- Apply strict thresholds in `RouteDecision` (including median, P95, jitter, failure counts); choose the lowest P95 among passing relays.
- Reconnect only the selected candidate, verify handshake, and repeat the benchmark before leaving it enabled. If confirmation fails, stop tunnel and remain direct.
- Never change routes during active play; match protection defaults ON.

## Hard limitations

- HTTPS request timing is **not** UDP gaming latency. Benchmark targets are public HTTPS endpoints, not Konami. Results are approximate and never guarantee in-game improvement.
- Android lockdown / 'Block connections without VPN' can prevent direct-network recovery. The app does not bypass OS policy.
- Speed cannot exceed the underlying link’s physical limitations. Remote relays add hops and can increase latency.
- A VPN config can expose traffic to the operator; use only relays you trust. Keys stay in process memory and are not written to GitHub or disk.
- Testing and switching VPN paths can transiently interrupt a live game; the optimizer intentionally requires an explicit pre-match button.
- APK compilation and real-phone tests for this version have not yet been certified.
