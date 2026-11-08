#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
usage() { echo 'Usage: sudo bash server/add-client.sh --name phone-name'; }
name=''
while (( $# )); do
    case "$1" in
        --name) (( $# >= 2 )) || fail 'Missing client name.'; name="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown argument: $1" ;;
    esac
done
validate_name "$name" || fail 'Invalid name; use letters, numbers, _ or -, starting with a letter.'
validate_interface "$IGRX_IF" || fail 'Invalid WireGuard interface.'
require_root
lock_state
load_metadata
require_command wg
[[ -e "$IGRX_CONF" ]] || fail 'Server config missing.'
[[ ! -e "$IGRX_DIR/clients/$name.conf" ]] || fail 'Client already exists.'
wg show "$IGRX_IF" listen-port >/dev/null || fail 'WireGuard interface is not running.'
client_index=''
for i in $(seq 2 254); do
    if [[ ! -e "$IGRX_DIR/allocated/$i" ]]; then client_index="$i"; break; fi
done
[[ -n "$client_index" ]] || fail 'No free IPv4 client addresses.'
umask 077
tmp_key="$(mktemp "$IGRX_DIR/.client-key.XXXXXXXX")"
trap 'rm -f "$tmp_key"' EXIT
wg genkey > "$tmp_key"
client_public="$(wg pubkey < "$tmp_key")"
server_public="$(cat "$IGRX_DIR/server.pub")"
client_ip="$WG_PREFIX.$client_index"
# Configure live session first; aborts without modifying the persistent file on failure.
wg set "$IGRX_IF" peer "$client_public" allowed-ips "$client_ip/32"
{
    printf '\n# IGRX1-CLIENT START %s\n' "$name"
    printf '[Peer]\nPublicKey = %s\nAllowedIPs = %s/32\n' "$client_public" "$client_ip"
    printf '# IGRX1-CLIENT END %s\n' "$name"
} >> "$IGRX_CONF"
chmod 600 "$IGRX_CONF"
client_conf="$IGRX_DIR/clients/$name.conf"
{
    printf '[Interface]\nPrivateKey = %s\nAddress = %s/32\nDNS = 1.1.1.1\n\n' "$(cat "$tmp_key")" "$client_ip"
    printf '[Peer]\nPublicKey = %s\nEndpoint = %s:%s\n' "$server_public" "$PUBLIC_ENDPOINT" "$WG_PORT"
    printf 'AllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\n'
} > "$client_conf"
chmod 600 "$client_conf"
printf '%s\n' "$client_public" > "$IGRX_DIR/peers/$name.pub"
printf '%s\n' "$client_index" > "$IGRX_DIR/allocated/$client_index"
chmod 600 "$IGRX_DIR/peers/$name.pub" "$IGRX_DIR/allocated/$client_index"
printf 'Created client %s (%s); private profile stored at %s (mode 0600).\n' "$name" "$client_ip" "$client_conf"
printf 'Transfer .conf privately to your phone; NEVER attach it to GitHub or publish it.\n'
