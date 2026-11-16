# Internal messaging security (0.1.4)

Both `mcmmoparties:teleport` and `mcmmoparties:partychat` now require HMAC-SHA256 authentication in both directions. Client-originated messages are consumed and rejected on Velocity. Backend identity comes from the actual connection, and the subject must currently belong to that backend. Chat sender name and source server are checked. Strict payload parsing rejects malformed UUIDs, numbers, text and recipient lists.

Signed envelopes bind sender, destination, channel, timestamp and nonce to the payload. Messages older than 60 seconds or more than 5 seconds in the future are rejected. A bounded nonce cache rejects replays and fails closed when full. Keep server clocks synchronized.

## Configuration and upgrade

Update Paper and Velocity together (or use the appropriate universal JAR). Version 0.1.3 plaintext packets are intentionally incompatible. No servers have been updated automatically.

`InternalMessaging.yml` is created in each plugin data directory at startup. On Paper this is normally `plugins/McMMOParties/`; on Velocity it is normally `plugins/mcmmopartiesvelocity/`.

For every backend, generate a separate cryptographically random Base64 key containing at least 32 decoded bytes. Put the same key in:

- Paper: `proxy.secret`
- Velocity: `servers.<backend-name>.secret`

Paper `config.yml` → `Network.ServerName` must exactly match that backend name in Velocity. The default `paper` must be changed if the registered backend has another name. The YAML includes all eight OpenMC backends plus a `paper` example. Paper reads its `proxy` section; Velocity reads `servers`.

Both peers must allow the required channel in `channels`. Use the fixed identifiers above. Empty keys, invalid keys, missing peers or denied channels disable the affected cross-server operation; there is no plaintext fallback. Local party operations remain available. Apply configuration changes by restarting the affected plugins/servers during your normal maintenance window.

No secrets are committed or generated into this repository. Do not reuse keys from other plugins. Rejections are reported locally with throttled, sanitized warnings, without secret or packet contents. Discord reporting is not included.

## Trust and verification

This protects against untrusted clients injecting internal packets. An authenticated backend remains trusted to request operations for players connected to it; a compromised backend or proxy requires separate remediation and key rotation.

Regression tests cover valid signed messages, tampering, wrong keys/routes, timestamps, replay, missing keys, allowlists, client injection, spoofed subjects, malformed payloads, and the actual Paper listeners. This change is a focused messaging-boundary fix, not a comprehensive dependency vulnerability audit. No live exploitation or live deployment was performed.
