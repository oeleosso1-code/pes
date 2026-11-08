# Verified implementation sources

- Official Android `VpnService` documentation: https://developer.android.com/develop/connectivity/vpn
- Official Android VPN API reference: https://developer.android.com/reference/android/net/VpnService
- WireGuard Android app/library: https://github.com/WireGuard/wireguard-android
- WireGuard Maven library: https://central.sonatype.com/artifact/com.wireguard.android/tunnel/1.0.20260102
- WireGuard GoBackend and per-app config: https://github.com/WireGuard/wireguard-android/blob/master/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java
- Official eFootball package ID: https://play.google.com/store/apps/details?id=jp.konami.pesam
- AGP 8.13 compatibility: https://developer.android.com/build/releases/agp-8-13-0-release-notes

## Additional designs evaluated — not copied or bundled

- Rethink DNS: https://github.com/celzero/rethink-app
- NetGuard: https://github.com/M66B/NetGuard
- sing-box-for-android: https://github.com/SagerNet/sing-box-for-android
- LibreSpeed: https://github.com/librespeed/speedtest
- Tailscale: https://github.com/tailscale/tailscale

Only WireGuard's Maven tunnel library and AndroidX runtime are integrated in this alpha. Other repositories are architecture references, not merged libraries. Code reuse must be separately audited for GPL/Apache/BSD/LGPL compatibility.
