#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
usage() { echo 'Usage: sudo bash server/remove-client.sh --name phone-name'; }
name=''
while (( $# )); do
    case "$1" in
        --name) (( $# >= 2 )) || fail 'Missing client name.'; name="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown argument: $1" ;;
    esac
done
validate_name "$name" || fail 'Invalid client name.'
validate_interface "$IGRX_IF" || fail 'Invalid WireGuard interface.'
require_root
lock_state
load_metadata
require_command wg
[[ -f "$IGRX_DIR/peers/$name.pub" ]] || fail 'Client does not exist.'
public="$(cat "$IGRX_DIR/peers/$name.pub")"
client_conf="$IGRX_DIR/clients/$name.conf"
client_index="$(sed -n 's/^Address = .*\.\([0-9][0-9]*\)\/32$/\1/p' "$client_conf")"
[[ "$client_index" =~ ^[0-9]{1,3}$ ]] || fail 'Client index invalid.'
# Remove active peer first. This revokes the credential at the live server.
wg set "$IGRX_IF" peer "$public" remove
newconf="$(mktemp "$IGRX_WG_DIR/.igrx1.XXXXXXXX")"
trap 'rm -f "$newconf"' EXIT
awk -v start="# IGRX1-CLIENT START $name" -v stop="# IGRX1-CLIENT END $name" '
$0 == start { skip=1; next }
$0 == stop  { skip=0; next }
!skip { print }
' "$IGRX_CONF" > "$newconf"
chmod 600 "$newconf"
mv "$newconf" "$IGRX_CONF"
rm -f "$client_conf" "$IGRX_DIR/peers/$name.pub" "$IGRX_DIR/allocated/$client_index"
printf 'Revoked peer %s. Note: client copies of the old private key must not be reused.\n' "$name"
