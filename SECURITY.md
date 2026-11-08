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

- Match protection only prevents automatic route changes by the app; it cannot guarantee connectivity or bypass Android lockdown mode.
- Direct/VPN comparison performs outbound HTTPS diagnostics to two public services. It never captures game traffic.

## Self-hosted relay (server edition 0.6)

- Run `server/install-relay.sh` only on a VPS that you administer. Verify commands before execution.
- Client `.conf` files and private keys stay under root-restricted `/etc/igrx1/` on the VPS; never publish them as GitHub Actions artifacts, logs, issue attachments, QR codes on public sites, or commit them.
- Allocate unique keys per device. Revoke a compromised peer with `server/remove-client.sh` and issue a new profile.
- SSH deployment accepts only pinned, previously verified host keys (`RELAY_SSH_KNOWN_HOSTS`). Do not blindly trust `ssh-keyscan` output.
- The server kit is currently IPv4-only, and is not a production-grade multitenant VPN access-control or billing service.
- Full deployment depends on authorized external VPS access, the provider firewall, and live on-device testing.
