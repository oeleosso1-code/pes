#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
validate_interface "$IGRX_IF" || fail 'Invalid WireGuard interface.'
require_root
load_metadata
require_command wg
require_command sysctl
printf 'Region: %s\nEndpoint: %s\nInterface: %s\nUDP port: %s\n' "$REGION" "$PUBLIC_ENDPOINT" "$IGRX_IF" "$WG_PORT"
[[ "$(sysctl -n net.ipv4.ip_forward)" == 1 ]] || fail 'IPv4 forwarding disabled.'
[[ "$(wg show "$IGRX_IF" listen-port)" == "$WG_PORT" ]] || fail 'WireGuard listener unavailable or wrong port.'
# Check that the installed WireGuard IPv4 network has an active NAT rule.
require_command iptables
require_command ip
[[ "$WAN_INTERFACE" =~ ^[A-Za-z0-9_.-]{1,32}$ ]] || fail 'Saved outbound interface is invalid.'
ip -4 route show default | grep -q 'default' || fail 'No outgoing default IPv4 route.'
iptables -t nat -C POSTROUTING -s "$WG_PREFIX.0/24" -o "$WAN_INTERFACE" -j MASQUERADE || fail 'Expected VPN NAT rule missing.'
printf 'IPv4 forwarding: enabled\nWireGuard local listener: active\nIPv4 NAT rule: installed\n'
printf 'Peers: %s\n' "$(wg show "$IGRX_IF" peers | awk 'NF>0{n++} END {print n+0}')"
printf 'IMPORTANT: local checks do NOT prove internet reachability from Iraq or lowered game ping.\n'
