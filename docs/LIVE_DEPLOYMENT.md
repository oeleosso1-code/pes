# Live deployment checklist — Iraq Game Route X1

**Status: deployment prerequisites documented; NO VPS has been created or operated from this repository.** Do not describe this as a working public game accelerator until the physical device tests below pass.

## Choose a host

- A legitimate Linux VPS with a public IPv4 address, Ubuntu 22.04/24.04, root/sudo via your own hosting account, and a public inbound UDP port. Confirm the provider permits private WireGuard VPN use.
- Providers publish official VPS setup instructions. For example, DigitalOcean Droplets and Hetzner Cloud both document VPN-capable Linux servers and firewall UDP rules. No provider is required or endorsed. Regions and peering must be evaluated from your ISP in Iraq; a nearby geography is not proof of a low ping.
- **Do not use GitHub Actions runner as a permanent VPN server.** It is temporary CI, not a persistent public endpoint.

## Deploy (only on an authorized VPS)

1. Create the VPS in your own hosting account. Use SSH key login, apply security updates, and restrict remote administration. Keep billing and account authentication private.
2. Configure provider-level firewall: allow required SSH access and inbound **UDP 51820** (or selected WireGuard port). Provider firewalls are separate from Linux UFW.
3. Review the repository's `server/install-relay.sh` and read the [relay guide](../server/README.md). Transfer the server folder to the VPS, then install one relay.
4. Generate **a separate** phone profile on the VPS using `sudo bash server/add-client.sh --name phone1`. Transfer the resulting `.conf` privately to the phone; never upload keys to GitHub, chat, or workflow artifacts.
5. Open the X1 Android app, import the profile, grant Android's VPN consent, connect, and open a webpage. Only then run `sudo bash server/verify-live-client.sh --name phone1` on the VPS.
6. If the handshake and traffic verification pass, separately test public IP and DNS connectivity on the phone, and compare direct vs VPN eFootball play. Restore the direct path if worse. **Never switch routes mid-match.**

## Verification stages

| Stage | Success evidence | If missing |
| --- | --- | --- |
| Installer | `systemctl is-active wg-quick@igrx1` | Check Ubuntu/kernel/port and installation logs |
| Local forwarding | `sudo bash server/health-check.sh` | Fix forwarding, NAT or firewall |
| Phone connected | `sudo bash server/verify-live-client.sh --name phone1` | Check UDP firewall, ISP access, profile and keys |
| Internet via relay | Phone HTTPS and public-IP check after enabling tunnel | Check NAT, DNS, tunnel routing |
| eFootball improved | Repeated in-match comparison over time | Use direct internet; VPS cannot guarantee an improvement |

> Privacy: host/login passwords, SSH keys, WireGuard private keys and client `.conf` files must never be posted into public repositories, logs or this conversation. A public IPv4 address and hosting provider name are generally non-secret infrastructure metadata.
