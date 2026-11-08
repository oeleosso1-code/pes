# Route benchmark methodology — v0.4

The aim is **least risky connection choice before a match**, not claiming access to
Konami's internal telemetry.

## Data collection

1. Both HTTP(S) paths use the same URL and request method.
2. Four ABBA/BAAB rounds compare direct and VPN for eight transactions per route.
3. Stop and discard the trial if the underlying Wi-Fi/cellular network changes,
   or the active VPN state is lost.
4. Where one route cannot reliably reach the primary endpoint, repeat the entire
   test with the independent secondary endpoint, for both routes.
5. Only measurements > 0 produced by HTTPS requests are used in summarization.

## Recommendation heuristic

- Require 8 measurements per route and >= 7 valid per route.
- The VPN must have a lower median by >= 15% AND >= 8 milliseconds.
- VPN P95, adjacent transaction-time variation and failed-request percentage
  must not be worse than the direct route.
- Recommend a **candidate**, never silently change active VPN routing.
- This is intentionally conservative but is not a formal hypothesis test.

## Major limitations

A connection transaction includes DNS, TCP, TLS and HTTP response time; it
cannot be described as game UDP ping or packet loss. The remote test service
can independently have congestion or throttle repeated requests, so repeat
tests under similar conditions on several occasions. P95 on 8 samples is
noisy; user validation in eFootball is still required.

Do not benchmark while playing an online match. Any relay change can terminate
the established game session. Do not enable Android VPN lockdown without knowing
how it impacts direct-connection fallback.
