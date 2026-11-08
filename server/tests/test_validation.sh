#!/usr/bin/env bash
set -Eeuo pipefail
# Offline tests: no hosting account, server privileges, network, or keys needed.
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/lib.sh"

assert_accept() { "$1" "$2" || { echo "Expected accepted: $1 $2" >&2; exit 1; }; }
assert_reject() { if "$1" "$2"; then echo "Expected rejected: $1 $2" >&2; exit 1; fi; }
assert_accept validate_host 'relay.example.com'
assert_accept validate_host '198.51.100.10'
assert_reject validate_host '999.20.30.40'
assert_reject validate_host 'x;rm -rf /'
assert_reject validate_host 'relay..example.com'
assert_reject validate_host '-relay.example.com'
assert_reject validate_host ''
assert_accept validate_interface 'igrx1'
assert_reject validate_interface 'wg0;bad'
assert_accept validate_region 'bahrain-1'
assert_reject validate_region 'Bahrain!!'
assert_accept validate_name 'android-phone_1'
assert_reject validate_name '../evil'
assert_reject validate_name '1wrong'
assert_accept validate_port '51820'
assert_reject validate_port '22'
assert_reject validate_port '65536'
assert_accept validate_prefix '10.207.20'
assert_reject validate_prefix '192.168.1'
assert_reject validate_prefix '10.999.1'
for script in install-relay add-client remove-client; do
    bash "$(dirname "${BASH_SOURCE[0]}")/../${script}.sh" --help >/dev/null
done
printf 'PASS: server input validation and non-privileged CLI smoke tests (21 checks)\n'
