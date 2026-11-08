# Relay infrastructure prerequisites

This Android application **does not include a relay service**. It cannot create a new internet route on its own or promise lower latency. You must acquire access to a reliable WireGuard server from a trusted provider or a server you operate, and a valid per-client `.conf`.

## Required properties

- The user has explicit authorization to use the relay.
- The server can route IPv4 traffic and has functional DNS access and outbound internet reachability.
- The WireGuard UDP port is reachable on the user's ISP; performance should be tested with the actual ISP.
- The client profile has `[Interface] PrivateKey` and `Address`, `[Peer] PublicKey`, `Endpoint` and `AllowedIPs = 0.0.0.0/0`. Do **not** paste any real keys into GitHub or chat.
- The provider has clear privacy, retention, billing, and acceptable-use policies.

## How to select a region

Measure each relay from the Iraqi ISP across multiple periods. A nearby city does not guarantee better routing: the network transit path, congestion, and the eFootball session target matter more than geographic proximity alone. The app's HTTPS transactions are only a diagnostic proxy and do not directly measure gameplay RTT.

## Safety policy

Never change relays while a match is in progress. Prefer the direct internet path until a relay has been tested. Android's `Block connections without VPN` setting may intentionally prevent direct fallback; the application cannot circumvent it. A relay may stop working without warning; there is no guarantee of zero disconnects.

## Release prerequisites

Obtain a dedicated test relay and client profile, run the Android build CI, perform real on-device functional tests, and compare actual in-game responsiveness over multiple matches. The project does not modify the eFootball client or intercept/decrypt the game's private protocol.
