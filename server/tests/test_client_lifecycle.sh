#!/usr/bin/env bash
# Offline end-to-end peer provisioning simulation. Does not create VPN or change network.
set -Eeuo pipefail
if [[ "$(id -u)" != 0 ]]; then
    # GitHub ubuntu runner supports sudo; keep test root-owned temp state private.
    exec sudo env PATH="$PATH" bash "$0"
fi
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sandbox="$(mktemp -d)"
trap 'rm -rf "$sandbox"' EXIT
mkdir -p "$sandbox/bin" "$sandbox/etc/clients" "$sandbox/etc/peers" "$sandbox/etc/allocated" "$sandbox/wireguard"
chmod 700 "$sandbox" "$sandbox/etc"
cat > "$sandbox/bin/wg" <<'MOCK'
#!/usr/bin/env bash
case "$1" in
    genkey) printf '%s\n' 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' ;;
    pubkey) cat >/dev/null; printf '%s\n' 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=' ;;
    show)
        case "${3:-}" in
            listen-port) echo 51820 ;;
            latest-handshakes) printf 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB= %s\n' "${MOCK_HANDSHAKE:-0}" ;;
            transfer) printf 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB= %s %s\n' "${MOCK_RX:-1024}" "${MOCK_TX:-2048}" ;;
        esac ;;
    set) exit 0 ;;
    *) exit 1 ;;
esac
MOCK
chmod +x "$sandbox/bin/wg"
cat > "$sandbox/etc/relay.env" <<'META'
PUBLIC_ENDPOINT=relay.example.com
REGION=test-1
WG_PORT=51820
WG_PREFIX=10.207.20
WAN_INTERFACE=eth0
META
printf '%s\n' 'CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=' > "$sandbox/etc/server.pub"
cat > "$sandbox/wireguard/igrx1.conf" <<'CONF'
[Interface]
Address = 10.207.20.1/24
CONF
export IGRX_DIR="$sandbox/etc" IGRX_WG_DIR="$sandbox/wireguard" PATH="$sandbox/bin:$PATH"
bash "$here/add-client.sh" --name phone1 > "$sandbox/add-output"
client="$sandbox/etc/clients/phone1.conf"
[[ -f "$client" && -f "$sandbox/etc/peers/phone1.pub" ]]
grep -q '^Address = 10.207.20.2/32$' "$client"
grep -q '^AllowedIPs = 0.0.0.0/0$' "$client"
grep -q '^Endpoint = relay.example.com:51820$' "$client"
grep -q '# IGRX1-CLIENT START phone1' "$sandbox/wireguard/igrx1.conf"
[[ "$(stat -c %a "$client")" == 600 ]]
! grep -q 'AAAAAAAAAAAAAAAAAAAA' "$sandbox/add-output"
if bash "$here/add-client.sh" --name phone1 > /dev/null 2>&1; then
    echo 'Expected duplicate client rejection' >&2; exit 1
fi
MOCK_HANDSHAKE="$(date +%s)" bash "$here/verify-live-client.sh" --name phone1 > "$sandbox/check-output"
grep -q '^PASS:' "$sandbox/check-output"
if MOCK_HANDSHAKE=0 bash "$here/verify-live-client.sh" --name phone1 >/dev/null 2>&1; then
    echo 'Missing handshake incorrectly passed' >&2; exit 1
fi
if MOCK_HANDSHAKE="$(( $(date +%s) - 600 ))" bash "$here/verify-live-client.sh" --name phone1 >/dev/null 2>&1; then
    echo 'Stale handshake incorrectly passed' >&2; exit 1
fi
if MOCK_HANDSHAKE="$(date +%s)" MOCK_RX=0 bash "$here/verify-live-client.sh" --name phone1 >/dev/null 2>&1; then
    echo 'Missing bidirectional traffic incorrectly passed' >&2; exit 1
fi
bash "$here/remove-client.sh" --name phone1 > /dev/null
[[ ! -e "$client" && ! -e "$sandbox/etc/allocated/2" ]]
! grep -q 'IGRX1-CLIENT' "$sandbox/wireguard/igrx1.conf"
printf 'PASS: isolated mock client create, duplicate protection, secret output, revoke and handshake/traffic diagnosis\n'
