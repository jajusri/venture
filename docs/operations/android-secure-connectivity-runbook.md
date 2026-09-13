# Android secure connectivity runbook

## Supported customer connection model

Venture Android customer builds connect through explicit secure pairing to the Desktop-managed
Connector. The phone and computer must be on the same trusted LAN. Customers do not enter a laptop
IP address, and the app does not use the Android-emulator address `10.0.2.2` in release builds.

The stored trust anchor is the Connector transport certificate fingerprint. LAN discovery can find
a new address after DHCP, Wi-Fi, or router changes, but a discovered address is accepted only after
the presented certificate matches the already-trusted fingerprint. Connector ID or mDNS metadata
alone is never sufficient to replace trust.

## First pairing or deliberate replacement

1. Start Venture Desktop and confirm the Connector and Tally are healthy.
2. Put the phone and computer on the same normal trusted Wi-Fi/LAN. Disable an active phone VPN if it
   prevents local-network access.
3. In Desktop, enable secure mobile pairing and generate a fresh QR code.
4. On Android, open **Settings > Manage secure pairing**.
5. For an already-paired phone, select **Replace / Re-pair Connector**. This explicit action does not
   erase the working credential first.
6. Scan the Desktop QR and wait for secure verification to complete.
7. Confirm the app reports a verified secure connection and the expected company.

Replacement is fail-closed. The existing active credential remains intact if QR parsing, redemption,
certificate verification, Connector/device identity validation, encryption, or storage fails. The
new active record is published only after the new endpoint and credential have been verified.
Opening pairing management for an already-active credential must remain on that screen until the
customer chooses an action. Returning to the Dashboard is permitted only after a pairing operation
has completed successfully. Cancelling, rejecting, or dismissing a failed replacement returns to
the actual persisted trust state; it must not make an active device appear unpaired.

## Normal reconnection

No QR scan or manual address should be needed after ordinary app, Desktop, Connector, Tally, Wi-Fi,
router, or DHCP restarts. The app performs bounded discovery, filters by the trusted Connector ID,
and cryptographically verifies the pinned certificate before updating only the endpoint location.

An old preserved company selection may legitimately outlive the Connector's session TTL. The app
recognizes the exact authenticated `SESSION_EXPIRED` response and performs one bounded renewal of
the already-saved company. The Connector treats that same-company selection as an idempotent,
durably persisted lease renewal. It does not clear trust, request another QR, weaken TLS pinning, or
retry indefinitely.

## Status interpretation

- **Secure connection verified**: a pinned, credential-authenticated request succeeded.
- **Secure pairing stored**: trust exists locally; the live status shown above is authoritative.
- **Could not reach the Connector**: a network/reachability failure; trust remains unchanged.
- **Connector identity changed**: a discovered endpoint presented a different certificate. The app
  does not accept it automatically. Confirm the correct Desktop and explicitly scan a fresh QR.
- **Connector certificate is invalid**: certificate validation failed. Trust remains unchanged.
- **Phone network connectivity**: describes the phone's network state, not proof of Connector health.
- **Session expired**: the secure connection remains trusted; one bounded company-session renewal
  is attempted automatically. It is not a pairing or certificate failure.

For a securely paired device, health and readiness are read from the trusted endpoint over pinned
TLS. These two routes are public by Connector contract and therefore receive no bearer credential;
the credential-authenticated diagnostics probe must succeed first. Business routes continue to
require the bearer credential.

## Safe troubleshooting order

1. Confirm Desktop, Connector, and Tally are running and healthy.
2. Confirm phone and computer are on the same trusted LAN and no VPN isolates local traffic.
3. Refresh once and allow bounded discovery to finish.
4. If an explicit identity-change message appears, verify the Desktop identity and use one deliberate
   replacement pairing. Never accept a fingerprint silently.
5. If the problem remains, capture the visible status and sanitized Desktop/Connector diagnostics.
   Do not clear Android app data, reinstall, use ADB, enter a manual IP, or weaken certificate checks
   as a first response.

Debug builds retain emulator/manual-server controls for development. Those controls and cleartext
network permissions are intentionally absent from customer release behavior.
