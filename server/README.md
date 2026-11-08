# IGRX1 Relay Provisioning (server edition 0.6)

Deploys **one private WireGuard IPv4 relay** to an owner-controlled, freshly provisioned Ubuntu 22.04/24.04 VPS. Repeat independently on multiple VPS hosts in different locations. The Android app imports a *different private client profile per relay* and chooses a candidate using measurements taken **before** playing.

## Prerequisites

- A **real VPS** with a dedicated public IPv4 address, root or passwordless sudo access, SSH access, and outbound internet access. Purchasing/allocating a VPS is NOT done by this repository.
- Ubuntu 22.04 or 24.04, systemd, default IPv4 route; provider/firewall must allow inbound UDP 51820 (or your chosen port), and SSH.
- IPv4 forwarding and NAT. Script configures these on a supported clean server. Ubuntu UFW is handled when active, but external/cloud firewalls must also be configured.
- A different private `--prefix` for every relay if you plan to interconnect multiple VPN networks. App-only peers on independent VPS hosts can use the default on each.

## Deploy on the VPS

Transfer the repository's `server/` directory privately to the server (for example using your VPS provider console, SFTP, or SSH). Inspect the script before running it; **never run commands from an untrusted repository**.

```bash
sudo bash server/install-relay.sh \
  --endpoint YOUR_PUBLIC_IPV4_OR_DNS \
  --region region-1 --port 51820 --prefix 10.207.20
```

The script refuses to overwrite an existing `igrx1` WireGuard interface. It installs `wireguard-tools`, sets up IPv4 forwarding/NAT, enables systemd's `wg-quick@igrx1`, and keeps private server files mode 0600 under `/etc/igrx1/` and `/etc/wireguard/`.

## Issue a distinct client configuration

```bash
sudo bash server/add-client.sh --name phone1
sudo bash server/health-check.sh
```

`phone1.conf` is saved under `/etc/igrx1/clients/phone1.conf` **on the VPS**, not in the repository. Transfer it privately to your Android device and import through X1's **استيراد ملف خادم جديد**. Do not put the file, screenshot, or private key into GitHub, chat, workflow artifacts, or public downloads. Sharing the same client key across phones is not supported. If a key leaks, revoke it:

```bash
sudo bash server/remove-client.sh --name phone1
sudo bash server/add-client.sh --name phone1
```

The generated client profile is full-tunnel **IPv4**. The Android application applies its own **per-app allowlist** on import. IPv6 egress is not tunneled by this server kit and needs separate engineering/testing. Keep the baseline direct internet path available; do not enable Android's `Block connections without VPN` unless you understand that it can prevent fallback.

## Safe GitHub Action deployment (optional)

The manual workflow `.github/workflows/deploy-relay.yml` can configure an **already existing** VPS over SSH, but it cannot buy or create one. Configure an environment named `relay-deployment`, and add four repository or environment secrets:

- `RELAY_SSH_HOST` — the VPS public IPv4/DNS
- `RELAY_SSH_USER` — an SSH user that can run `sudo -n` on that host
- `RELAY_SSH_PRIVATE_KEY` — the deployment SSH key (**not** a WireGuard private key)
- `RELAY_SSH_KNOWN_HOSTS` — the VPS **verified** SSH host-key line; obtain/verify its fingerprint from your hosting provider, **not** blind `ssh-keyscan`

Then select **Actions → Deploy IGRX1 relay to existing VPS → Run workflow** and fill in the public endpoint, region label, port, and subnet prefix. Do not paste secrets into those inputs. The action never outputs client private keys or publishes `.conf` artifacts. Client creation is intentionally a **separate, authorized, private step on the VPS**.

## Server verification limits

`health-check.sh` checks local IPv4 forwarding and WireGuard listener, but cannot prove provider firewall openness, successful handshakes from Iraq, or a lower eFootball RTT. Run actual private-device connection and before/after in-game tests. A VPN may make ping worse; do not auto-rotate during matches.

## Design / security

- The project has no access to your hosting accounts, so **no public server has been deployed by this repository alone**.
- Only one WireGuard interface `igrx1` is managed per host. The scripts do not touch other VPN configurations.
- Every client has a separate WireGuard key/address; reusing a profile is discouraged and revocation supported.
- Root-owned client configurations are plaintext at rest on the VPS: protect backups, VPS disk access, and administration credentials. Rotate credentials if compromised.
- Script uses iptables compatible with Ubuntu 22.04/24.04 (normally iptables-nft) and UFW hooks. If custom network firewall tools are installed, review forwarding/NAT interaction first.
- The relay is not a gaming accelerator by itself; cloud provider routing may be less efficient than direct. Use only your own servers or a provider that permits WireGuard/VPN usage.

## Live connectivity verification (server edition 0.7)

The relay can report **success locally** even when a VPS firewall blocks UDP from the phone. To confirm that the Android client really reached WireGuard, connect the client's VPN on the phone, open a normal webpage, then run **on the VPS**:

```bash
sudo bash server/verify-live-client.sh --name phone1
```

This requires a **fresh** WireGuard handshake and nonzero encrypted receive/send byte counters for that specific client. It prints no private keys and cannot prove lower eFootball latency. A failed check means the phone is not yet verified; inspect the VPS firewall, Android VPN permission, client profile, and public UDP access. Do not switch to gaming with a failed live test.

The health check also verifies that the expected IPv4 MASQUERADE rule exists, in addition to checking forwarding and WireGuard's listener. This still cannot test upstream routing or access from an Iraqi ISP. Use **only** a real, publicly reachable VPS. The installer rejects private and documentation IPv4 literals to prevent false 'deployed' claims.

### Explicit deployment status

1. **Source published:** files appear in GitHub.
2. **VPS installed:** `wg-quick@igrx1` is active on the VPS, which is NOT established by GitHub publication.
3. **Phone verified:** `verify-live-client.sh` succeeds after real phone traffic.
4. **Game benefit tested:** multiple real eFootball matches show better responsiveness or fewer disruptions. This is not guaranteed by steps 1–3.
