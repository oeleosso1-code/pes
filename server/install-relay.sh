#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

usage() {
    cat <<'HELP'
Usage: sudo bash server/install-relay.sh --endpoint PUBLIC_IPV4_OR_HOSTNAME \
    --region short-region-code [--port 51820] [--prefix 10.207.20]
Creates one WireGuard IPv4 relay on a NEW Ubuntu 22.04/24.04 VPS.
Does NOT purchase hosting, allocate a public IP, or create a client until add-client.sh.
Does NOT overwrite an existing WireGuard interface or emit private keys to output.
HELP
}
endpoint='' region='' port='51820' prefix='10.207.20'
while (( $# )); do
    case "$1" in
        --endpoint|--region|--port|--prefix)
            (( $# >= 2 )) || fail "Missing value for $1"
            case "$1" in
                --endpoint) endpoint="$2" ;;
                --region) region="$2" ;;
                --port) port="$2" ;;
                --prefix) prefix="$2" ;;
            esac
            shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Unknown argument $1" ;;
    esac
done
validate_public_endpoint "$endpoint" || fail 'Invalid --endpoint. Use a public IPv4 or DNS name.'
validate_region "$region" || fail 'Invalid --region. Example: bahrain-1.'
validate_port "$port" || fail 'Invalid UDP port (1024..65535).'
validate_prefix "$prefix" || fail 'Invalid --prefix (private network 10.x.y).'
validate_interface "$IGRX_IF" || fail 'Invalid WireGuard interface.'
require_root
[[ -f /etc/os-release ]] || fail 'Linux distribution not recognized.'
# shellcheck disable=SC1091
source /etc/os-release
[[ "$ID" == ubuntu && ( "$VERSION_ID" == 22.04 || "$VERSION_ID" == 24.04 ) ]] || fail 'Supported on Ubuntu 22.04 or 24.04.'
lock_state
[[ ! -e "$IGRX_CONF" && ! -e "$IGRX_DIR/relay.env" ]] || fail 'Relay already provisioned; refusing to overwrite.'
require_command ip
wan="$(ip -4 route show default | awk '$1 == "default" {for(i=1;i<=NF;i++) if($i=="dev") {print $(i+1); exit}}')"
[[ "$wan" =~ ^[A-Za-z0-9_.-]{1,32}$ ]] || fail 'Could not determine safe outbound interface.'
[[ "$wan" != "$IGRX_IF" ]] || fail 'Default route cannot be the tunnel.'
if ip -4 route show | awk '{print $1}' | grep -Fxq "$prefix.0/24"; then
    fail 'Tunnel subnet conflicts with an existing exact route. Choose another --prefix.'
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq wireguard-tools iptables iproute2 util-linux
require_command wg
require_command wg-quick
require_command systemctl
umask 077
install -d -m 0700 "$IGRX_WG_DIR" "$IGRX_DIR" "$IGRX_DIR/clients" "$IGRX_DIR/peers" "$IGRX_DIR/allocated"
key="$IGRX_DIR/server.key"
wg genkey > "$key"
chmod 600 "$key"
wg pubkey < "$key" > "$IGRX_DIR/server.pub"
chmod 600 "$IGRX_DIR/server.pub"
cat > /etc/sysctl.d/90-igrx1-ipv4.conf <<'CONF'
net.ipv4.ip_forward=1
CONF
sysctl -q -p /etc/sysctl.d/90-igrx1-ipv4.conf
server_private="$(cat "$key")"
cat > "$IGRX_CONF" <<CONF
[Interface]
Address = $prefix.1/24
ListenPort = $port
PrivateKey = $server_private
SaveConfig = false
PostUp = iptables -C FORWARD -i $IGRX_IF -j ACCEPT || iptables -I FORWARD -i $IGRX_IF -j ACCEPT
PostUp = iptables -C FORWARD -o $IGRX_IF -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT || iptables -I FORWARD -o $IGRX_IF -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
PostUp = iptables -t nat -C POSTROUTING -s $prefix.0/24 -o $wan -j MASQUERADE || iptables -t nat -A POSTROUTING -s $prefix.0/24 -o $wan -j MASQUERADE
PostDown = iptables -D FORWARD -i $IGRX_IF -j ACCEPT 2>/dev/null || true
PostDown = iptables -D FORWARD -o $IGRX_IF -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || true
PostDown = iptables -t nat -D POSTROUTING -s $prefix.0/24 -o $wan -j MASQUERADE 2>/dev/null || true
CONF
chmod 600 "$IGRX_CONF"
{
  printf 'PUBLIC_ENDPOINT=%q\n' "$endpoint"
  printf 'REGION=%q\n' "$region"
  printf 'WG_PORT=%q\n' "$port"
  printf 'WG_PREFIX=%q\n' "$prefix"
  printf 'WAN_INTERFACE=%q\n' "$wan"
} > "$IGRX_DIR/relay.env"
chmod 600 "$IGRX_DIR/relay.env"
# ufw is optional; the cloud provider must independently allow incoming UDP.
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
    ufw allow "$port/udp" > /dev/null
    ufw route allow in on "$IGRX_IF" out on "$wan" > /dev/null
fi
systemctl enable --now "wg-quick@$IGRX_IF"
wg show "$IGRX_IF" listen-port > /dev/null || fail 'WireGuard interface is not active.'
printf 'Relay installed: region=%s interface=%s udp=%s\n' "$region" "$IGRX_IF" "$port"
printf 'Next: open UDP %s in your VPS provider firewall; run server/add-client.sh --name YOUR_PHONE.\n' "$port"
printf 'Private server and client keys never belong in GitHub.\n'
