#!/usr/bin/env bash
# Verify one client's recent handshake and actual encrypted traffic at the relay.
# This is a server-side connectivity check, NOT an eFootball ping benchmark.
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
usage() {
    printf '%s\n' 'Usage: sudo bash server/verify-live-client.sh --name phone1 [--max-age 180]'
    printf '%s\n' 'After connecting the phone, open a webpage through the VPN, then run this check.'
}
name='' max_age=180
while (( $# )); do
    case "$1" in
        --name|--max-age)
            (( $# >= 2 )) || fail "Missing argument for $1"
            case "$1" in
                --name) name="$2" ;;
                --max-age) max_age="$2" ;;
            esac
            shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown argument: $1" ;;
    esac
done
validate_name "$name" || fail 'Invalid client name.'
[[ "$max_age" =~ ^[0-9]{1,5}$ ]] && (( 10#$max_age >= 30 && 10#$max_age <= 3600 )) || fail 'Invalid --max-age (30..3600).'
validate_interface "$IGRX_IF" || fail 'Invalid interface name.'
require_root
load_metadata
require_command wg
peer_file="$IGRX_DIR/peers/$name.pub"
[[ -r "$peer_file" ]] || fail 'Client not provisioned. Run add-client.sh first.'
public="$(cat "$peer_file")"
[[ "$public" =~ ^[A-Za-z0-9+/]{43}=$ ]] || fail 'Saved peer public key is malformed.'
handshake="$(wg show "$IGRX_IF" latest-handshakes | awk -v k="$public" '$1 == k { print $2; found=1; exit } END { if (!found) print "" }')"
[[ "$handshake" =~ ^[0-9]{1,12}$ ]] && (( 10#$handshake > 0 )) || fail 'No WireGuard handshake received yet. Connect from the phone.'
now="$(date +%s)"
age="$(( now - handshake ))"
(( age >= -30 )) || fail 'Server clock appears incorrect (handshake is in the future).'
(( age <= 10#$max_age )) || fail "Last client handshake is stale ($age seconds ago)."
traffic="$(wg show "$IGRX_IF" transfer | awk -v k="$public" '$1 == k { print $2, $3; found=1; exit } END { if (!found) print "" }')"
read -r rx tx <<< "$traffic"
[[ "${rx:-}" =~ ^[0-9]+$ && "${tx:-}" =~ ^[0-9]+$ ]] || fail 'WireGuard traffic counters unavailable.'
(( rx > 0 && tx > 0 )) || fail 'Handshake exists, but no bidirectional application traffic yet. Open a webpage through the tunnel.'
printf 'PASS: %s has a fresh handshake (%ss) and encrypted data in both directions.\n' "$name" "$age"
printf 'Received: %s bytes; Sent: %s bytes.\n' "$rx" "$tx"
printf '%s\n' 'LIMIT: This proves only WireGuard relay traffic, not public UDP accessibility for other ISPs or faster eFootball response.'
