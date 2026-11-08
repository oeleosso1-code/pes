# Security and privacy

- Never commit `.conf` files, private keys, tokens or VPN certificates.
- Import config from Android's file picker; configs remain in memory, without disk persistence.
- Do not log `Config.toWgQuickString()` or raw parsing exceptions (they can contain private keys).
- Only run one user-authorized WireGuard tunnel; no DNS manipulation outside the tunnel.
- Require Android VPN consent each time needed, never disable OS safety policies.
- Use a trusted relay: the server operator is a privileged network observer.
- No analytics SDK and no background telemetry; checks send HTTPS requests to documented public endpoints.
- No reverse engineering, memory tampering, packet interception, or gameplay automation targeting eFootball.
- No claims about bypassing KONAMI matchmaking or account restrictions.

To report issues, use private communication with project maintainer rather than posting keys in GitHub issues.
