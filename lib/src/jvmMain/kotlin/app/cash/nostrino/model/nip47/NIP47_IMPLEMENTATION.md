# NIP-47 (Nostr Wallet Connect) Implementation Plan

## Overview

NIP-47 enables remote control of Lightning wallets through encrypted Nostr messages. Clients can send commands (pay invoice, get balance, etc.) to wallet services over Nostr relays using end-to-end encryption.

**Specification**: https://github.com/nostr-protocol/nips/blob/master/47.md

## Architecture

### Connection Flow
1. User scans QR/URI from wallet: `nostr+walletconnect://<wallet-pubkey>?relay=...&secret=...`
2. Client parses URI, derives connection keys
3. Client fetches wallet capabilities (kind 13194)
4. Client sends encrypted commands (kind 23194)
5. Wallet responds with encrypted results (kind 23195)
6. Wallet optionally sends notifications (kind 23197/23196)

### Encryption Strategy
- **Pluggable encryption**: `EncryptionEngine` SPI supports NIP-04 and future NIP-44
- **NIP-04 (default)**: AES-256-CBC, ships with library
- **NIP-44 (optional)**: XChaCha20-Poly1305, future optional module
- **Backward compatibility**: Wallets/clients negotiate best supported scheme

### Event Types (✅ Complete)

| Kind  | Type                 | Description                          | Status |
|-------|----------------------|--------------------------------------|--------|
| 13194 | Nip47Info            | Wallet capabilities & encryption     | ✅     |
| 23194 | Nip47Request         | Client command (encrypted payload)   | ✅     |
| 23195 | Nip47Response        | Wallet response (encrypted payload)  | ✅     |
| 23197 | Nip47Notification    | Notifications (NIP-44)               | ✅     |
| 23196 | Nip47NotificationLegacy | Notifications (NIP-04, deprecated) | ✅     |

## Implementation Progress

### ✅ Phase 1: Event Models & Encryption (Complete)
- [x] Nip47Info, Nip47Request, Nip47Response, Nip47Notification models
- [x] EncryptionTag, NotificationsTag for NIP-47 tags
- [x] EncryptionEngine SPI interface
- [x] Nip04Engine implementation
- [x] Arb generators for all message types
- [x] Comprehensive tests (22 tests, all passing)

### 🔄 Phase 2: Protocol Layer (In Progress)
- [ ] **JSON-RPC payload models** (nostrino-b3f)
  - RequestPayload(method, params)
  - ResponsePayload(result_type, error, result)
  - ErrorPayload(code, message)
  - Moshi adapters
- [ ] **Typed command models** (nostrino-mwh)
  - PayInvoice.Params/Result
  - GetBalance.Params/Result
  - GetInfo.Params/Result
  - MakeInvoice.Params/Result
  - LookupInvoice.Params/Result

### 📋 Phase 3: Client Layer (Planned)
- [ ] **NwcUri parser** (nostrino-aot)
  - Parse `nostr+walletconnect://` URIs
  - Validate pubkey, secret, relay parameters
- [ ] **NwcSession client** (nostrino-fsx)
  - fetchInfo(): Get wallet capabilities
  - sendCommand(): Execute typed commands
  - notifications(): Flow of wallet events
  - Request/response correlation
- [ ] **Event.content() integration** (nostrino-5fj)
  - Handle kinds 13194, 23194, 23195, 23197, 23196 in Event parsing

### 🧪 Phase 4: Testing & Integration (Planned)
- [ ] **FakeRelay tests** (nostrino-r7f)
  - URI parsing validation
  - Info fetch flow
  - Request/response round-trip
  - Error handling
  - Notification delivery
- [ ] **Notification support** (nostrino-d2g)
  - payment_received events
  - payment_sent events

### 🔮 Phase 5: Optional Features (Future)
- [ ] **NIP-44 encryption module** (nostrino-g68)
  - Nip44Engine with libsodium
  - XChaCha20-Poly1305 + HKDF
  - Platform compatibility (JVM, Android)
- [ ] **Advanced commands** (nostrino-sje)
  - ListTransactions
  - PayKeysend
  - MultiPayInvoice
  - MultiPayKeysend

## Design Decisions

### Package Structure
```
lib/src/jvmMain/kotlin/app/cash/nostrino/
├── model/nip47/          # Event models (this package)
│   ├── Nip47Info.kt
│   ├── Nip47Request.kt
│   ├── Nip47Response.kt
│   └── Nip47Notification.kt
├── nip47/                # Protocol & client (future)
│   ├── NwcUri.kt
│   ├── NwcSession.kt
│   ├── commands/         # Typed command models
│   └── protocol/         # JSON-RPC payloads
└── crypto/
    ├── EncryptionEngine.kt
    └── Nip04Engine.kt
```

### Key Patterns
1. **Reuse existing infrastructure**: NwcSession wraps existing `Relay` class
2. **Thin EventContent wrappers**: Models hold encrypted strings, decryption happens in client layer
3. **Type-safe commands**: Strongly-typed Params/Result models, flexible JSON underneath
4. **Sociable testing**: FakeRelay exercises full workflow without real relays

## Commands (NIP-47 Spec)

### Core Commands
- `pay_invoice`: Pay bolt11 invoice
- `get_balance`: Query wallet balance
- `make_invoice`: Create new invoice
- `lookup_invoice`: Check invoice status
- `get_info`: Wallet node information
- `list_transactions`: Query payment history

### Optional Commands
- `pay_keysend`: Send keysend payment
- `multi_pay_invoice`: Batch invoice payments
- `multi_pay_keysend`: Batch keysend payments
- `notifications`: Subscribe to wallet events

## Error Codes
- `RATE_LIMITED`: Too many requests
- `NOT_IMPLEMENTED`: Command not supported
- `INSUFFICIENT_BALANCE`: Not enough funds
- `QUOTA_EXCEEDED`: Spending limit reached
- `RESTRICTED`: Permission denied
- `UNAUTHORIZED`: No wallet connected
- `INTERNAL`: Server error
- `UNSUPPORTED_ENCRYPTION`: Encryption scheme not supported
- `PAYMENT_FAILED`: Payment did not complete
- `NOT_FOUND`: Resource not found
- `OTHER`: Unspecified error

## Next Steps

1. **Immediate**: Implement JSON-RPC payload models (beads issue: nostrino-b3f)
2. **Then**: Add typed command models (beads issue: nostrino-mwh)
3. **After**: Build NwcUri parser and NwcSession client
4. **Finally**: Comprehensive integration tests with FakeRelay

## References

- [NIP-47 Specification](https://github.com/nostr-protocol/nips/blob/master/47.md)
- [NIP-04 Encryption](https://github.com/nostr-protocol/nips/blob/master/04.md)
- [NIP-44 Encryption](https://github.com/nostr-protocol/nips/blob/master/44.md)
- Beads Epic: `nostrino-fd1`
