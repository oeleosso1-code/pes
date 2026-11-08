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
printf 'IPv4 forwarding: enabled\nWireGuard local listener: active\n'
printf 'Peers: %s\n' "$(wg show "$IGRX_IF" peers | awk 'NF>0{n++} END {print n+0}')"
printf 'IMPORTANT: local checks do NOT prove internet reachability from Iraq or lowered game ping.\n'
