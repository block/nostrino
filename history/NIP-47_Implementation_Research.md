# Research: NIP-47 (Nostr Wallet Connect) Implementation

**Date**: December 17, 2025  
**Issue**: https://github.com/block/nostrino/issues/69  
**Researcher**: Amp

## Research Question

Research the Nostrino codebase to understand existing architecture patterns and determine what components exist for implementing NIP-47 (Nostr Wallet Connect), which enables clients to interact with remote Lightning wallets through encrypted Nostr messages.

## Summary

NIP-47 defines a protocol for wallet interactions using Nostr events with specific event kinds (13194, 23194, 23195, 23196/23197) and requires NIP-44 encryption. The codebase has established patterns for implementing NIPs, including event models, relay communication, and encryption. However, **NIP-44 encryption is not yet implemented** - only NIP-04 (AES-CBC) exists, which NIP-47 requires for backward compatibility but should prefer NIP-44.

## NIP-47 Requirements

### Event Kinds Required
- **Kind 13194**: Info event (replaceable) - published by wallet service indicating capabilities
- **Kind 23194**: Request event - client sends encrypted commands to wallet service
- **Kind 23195**: Response event - wallet service responds with encrypted results
- **Kind 23196**: Notification event (NIP-04 encrypted, legacy)
- **Kind 23197**: Notification event (NIP-44 encrypted, preferred)

### Commands to Implement
1. `pay_invoice` - Pay a Lightning invoice
2. `multi_pay_invoice` - Pay multiple invoices
3. `pay_keysend` - Make a keysend payment
4. `multi_pay_keysend` - Multiple keysend payments
5. `make_invoice` - Create an invoice
6. `lookup_invoice` - Look up invoice status
7. `list_transactions` - List invoices/payments
8. `get_balance` - Get wallet balance
9. `get_info` - Get wallet service info

### Notification Types
- `payment_received` - Wallet received a payment
- `payment_sent` - Wallet sent a payment

### Encryption Requirements
- **Primary**: NIP-44 encryption (`nip44_v2`)
- **Backward compatibility**: NIP-04 encryption (`nip04`)
- Encryption negotiation via `encryption` tag in info and request events

## Detailed Findings

### 1. Existing Event Architecture

#### Event Base Class
[Event.kt:27-98](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt#L27-L98)

The base `Event` class represents Nostr events with:
- `id: ByteString` - Event ID (SHA256 hash)
- `pubKey: ByteString` - Author's public key
- `createdAt: Instant` - Timestamp
- `kind: Int` - Event kind number
- `tags: List<List<String>>` - Event tags
- `content: String` - JSON-encoded or plain content
- `sig: ByteString` - Schnorr signature

The `content()` method at [Event.kt:49-83](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt#L49-L83) deserializes the content string into typed `EventContent` instances based on the `kind` field using a when statement.

#### EventContent Interface
[EventContent.kt:27-54](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EventContent.kt#L27-L54)

All event types implement `EventContent`:
- `kind: Int` - Event kind number
- `tags: List<Tag>` - Typed tag list
- `toJsonString(): String` - Serialize content to JSON
- `sign(sec: SecKey): Event` - Create signed event

#### Existing Event Implementations

**Kind 9734 - ZapRequest** ([ZapRequest.kt:27-49](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/ZapRequest.kt#L27-L49))
- Shows pattern for events with complex tag structures
- Demonstrates how to extract typed data from tags
- Good reference for NIP-47 request/response structure

**Kind 4 - EncryptedDm** ([EncryptedDm.kt:29-46](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt#L29-L46))
- Uses `CipherText` for encryption
- Implements NIP-04 encryption only
- Pattern: `constructor(from: SecKey, to: PubKey, message: String)`
- Decryption: `decipher(from: PubKey, to: SecKey): String`

### 2. Encryption Implementation

#### Current State: NIP-04 Only

**CipherText** ([CipherText.kt:24-48](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt#L24-L48))
- Represents encrypted text with ciphertext and IV
- Format: `${cipherText.base64()}?iv=${iv.base64()}`
- Uses `SecKey.sharedSecretWith(PubKey)` for ECDH key derivation
- Decrypts using `AesCipher.decrypt()`

**AesCipher (JVM)** ([AesCipher.kt:24-43](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt#L24-L43))
- Algorithm: `AES/CBC/PKCS5Padding`
- Implements NIP-04 encryption
- Methods:
  - `encrypt(plainText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray`
  - `decrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray`
  - `generateIv(): ByteArray` - 16-byte random IV

**SecKey Shared Secret** ([SecKey.kt:45-46](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt#L45-L46))
- `sharedSecretWith(pub: PubKey): ByteArray` - ECDH shared secret derivation
- Uses `EcdhProvider.sharedSecret()`

#### Missing: NIP-44 Implementation

**Search Result**: No NIP-44 implementation found in the codebase.

NIP-44 requirements:
- Different encryption scheme than NIP-04
- Must be supported for NIP-47 (preferred over NIP-04)
- Requires ChaCha20-Poly1305 or similar AEAD cipher
- Different key derivation and padding than NIP-04

### 3. Relay Communication

#### RelayClient
[RelayClient.kt:53-163](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L53-L163)

Handles WebSocket communication with a single relay:

**Sending Events** ([RelayClient.kt:97-100](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L97-L100)):
```kotlin
override fun send(event: Event) {
  logger.info { "Enqueuing: ${event.id} [relay=$url]" }
  send(listOf("EVENT", event))
}
```

**Subscriptions** ([RelayClient.kt:102-110](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L102-L110)):
```kotlin
override fun subscribe(filters: Set<Filter>, subscription: Subscription): Subscription = subscription.also {
  send(listOf<Any>("REQ", it.id).plus(filters.toList()))
  subscriptions[subscription] = filters
}

override fun unsubscribe(subscription: Subscription) {
  subscriptions.remove(subscription)
  send(listOf("CLOSE", subscription.id))
}
```

**Message Flow** ([RelayClient.kt:69](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L69)):
- `relayMessages: Flow<RelayMessage>` - All incoming relay messages
- `allEvents: Flow<Event>` - Filtered to just events ([RelayClient.kt:112](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L112))

**Connection Management**:
- WebSocket with 20-second ping interval ([RelayClient.kt:55](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L55))
- Message queue with replay buffer (512 messages) ([RelayClient.kt:59](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L59))
- Automatic reconnection on failure ([RelayClient.kt:138-144](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt#L138-L144))

### 4. Event Processing Pattern

Looking at how existing complex events are structured:

#### Pattern from Event.kt
[Event.kt:53-83](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt#L53-L83)

```kotlin
fun content(): EventContent {
  val tags = tags.map { Tag.parseRaw(it) }
  val taggedPubKeys by lazy { tags.filterIsInstance<PubKeyTag>().map { it.pubKey } }
  val taggedEventIds by lazy { tags.filterIsInstance<EventTag>().map { it.eventId } }
  return when (this.kind) {
    TextNote.kind -> TextNote(content, tags)
    EncryptedDm.kind -> EncryptedDm(taggedPubKeys.first(), CipherText.parse(content), tags)
    // ... more kinds
    else -> adapters[this.kind]?.fromJson(content)!!.copy(tags = tags)
  }
}
```

For NIP-47:
- Request/Response events will need to parse encrypted JSON content
- Must handle `encryption` tag to determine NIP-04 vs NIP-44
- Must handle `p` tag for pubkey
- Response must handle `e` tag for request event ID

### 5. Tag System

Existing tag types that are relevant:
- `PubKeyTag` - `["p", "<pubkey>"]`
- `EventTag` - `["e", "<event-id>"]`

New tags needed for NIP-47:
- `EncryptionTag` - `["encryption", "nip44_v2"]` or `["encryption", "nip04"]`
- `NotificationsTag` - `["notifications", "payment_received payment_sent"]`

### 6. Filter System

[Filter.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Filter.kt) supports filtering by:
- Event kinds
- Authors (pubkeys)
- Event IDs
- Tags
- Time ranges

For NIP-47:
- Wallet service subscribes to kind 23194 with filter on its pubkey
- Client subscribes to kind 23195 and 23197 with filter on its pubkey

## Architecture Patterns Found

### 1. Event Model Pattern
1. Create data class implementing `EventContent`
2. Define companion object with `const val kind`
3. Implement `toJsonString()` for content serialization
4. Add deserialization in `Event.content()` when statement
5. Add Moshi adapter if using complex JSON (see UserMetaData pattern)

### 2. Encryption Pattern
1. `SecKey.encrypt()` extension function creates `CipherText`
2. `CipherText.decipher()` decrypts using recipient's SecKey and sender's PubKey
3. ECDH shared secret via `SecKey.sharedSecretWith(PubKey)`
4. Platform-specific `AesCipher` actual implementations

### 3. Relay Communication Pattern
1. `RelayClient.send(event)` for publishing
2. `RelayClient.subscribe(filters, subscription)` for listening
3. `relayMessages` Flow for all messages
4. `allEvents` Flow for filtered events
5. Message queuing with replay buffer

### 4. Tag Pattern
1. Sealed interface `Tag` with implementations
2. `toJsonList()` serialization
3. `parseRaw()` deserialization
4. Type-safe filtering via `filterIsInstance<>`

## Code References

### Event Models
- [Event.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt) - Base event class
- [EventContent.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EventContent.kt) - Event content interface
- [ZapRequest.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/ZapRequest.kt) - Complex event example
- [EncryptedDm.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt) - Encrypted event example

### Encryption
- [AesCipher.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt) - NIP-04 encryption (JVM)
- [CipherText.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt) - Cipher text wrapper
- [SecKey.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt) - Secret key with ECDH

### Relay Communication
- [RelayClient.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelayClient.kt) - WebSocket relay client
- [Relay.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/Relay.kt) - Abstract relay interface
- [RelaySet.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/client/RelaySet.kt) - Multi-relay manager

### Tags & Filters
- [Tag.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Tag.kt) - Tag definitions
- [Filter.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Filter.kt) - Event filtering

### Testing
- [RelayClientTest.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/client/RelayClientTest.kt) - Integration tests with Docker relay
- [EncryptedDmTest.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt) - NIP-04 encryption tests

## Key Implementation Gaps

### Critical: NIP-44 Encryption Missing
NIP-47 requires NIP-44 encryption as the primary encryption method. The codebase only has NIP-04 (AES-CBC). NIP-44 needs:
- Different encryption algorithm (likely ChaCha20-Poly1305 or XChaCha20-Poly1305)
- Different key derivation
- Different padding scheme
- Implementation in `commonMain` with platform-specific actuals

### New Event Kinds Needed
- Kind 13194: `WalletServiceInfo`
- Kind 23194: `WalletRequest`
- Kind 23195: `WalletResponse`
- Kind 23196: `WalletNotificationNip04` (legacy)
- Kind 23197: `WalletNotificationNip44`

### New Tag Types Needed
- `EncryptionTag` - `["encryption", "nip44_v2 nip04"]`
- `NotificationsTag` - `["notifications", "payment_received payment_sent"]`
- `DTag` - `["d", "<identifier>"]` for multi-pay responses

### Request/Response Model Needed
JSON-RPC style structure for encrypted payloads:
- Request: `{ method: string, params: object }`
- Response: `{ result_type: string, error?: object, result?: object }`
- Notification: `{ notification_type: string, notification: object }`

### Connection URI Parser Needed
Parse `nostr+walletconnect://` URIs with query parameters:
- `relay` - Relay URL(s)
- `secret` - 32-byte hex secret
- `lud16` - Lightning address (optional)

## Testing Patterns

From [RelayClientTest.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/client/RelayClientTest.kt):
- Docker-based relay at `ws://localhost:7707`
- Use Kotest StringSpec for test structure
- Use Kotest matchers (`shouldBe`, `shouldContainExactly`)
- Use Turbine for Flow testing
- Use Arb generators for test data

Integration test pattern:
1. Start relay client
2. Subscribe to events
3. Publish events
4. Collect and verify received events
5. Verify relay integration end-to-end

## Open Questions

1. **Wallet integration**: NIP-47 requires actual Lightning wallet interaction. How should this be abstracted? Interface-based design? Mock implementations for testing?

2. **Connection management**: Should there be a dedicated `WalletConnectClient` class that manages the NIP-47 protocol flow, or should it be built into `RelayClient`?

3. **Multi-relay support**: NIP-47 URIs can specify multiple relays. Should this use `RelaySet` or have custom logic?

4. **Ephemeral events**: NIP-47 uses ephemeral events. Does Nostrino need special handling for these, or does relay behavior handle it?

5. **Metadata storage**: NIP-47 allows up to 4KB of metadata per transaction. Where should this be stored? In-memory? Persistent storage?

6. **Error handling**: NIP-47 defines specific error codes. Should these be typed enums or strings?

7. **NIP-44 implementation**: Should we implement full NIP-44 spec, or minimal subset for NIP-47?

## Related NIPs

- **NIP-01**: Basic protocol (already implemented)
- **NIP-04**: Encrypted direct messages (already implemented) - needed for backward compatibility
- **NIP-44**: Encrypted payloads (NOT implemented) - required for NIP-47
- **NIP-47**: Nostr Wallet Connect (to be implemented)
- **NIP-57**: Zaps (already implemented) - similar wallet interaction pattern

## Next Steps (Not Recommendations - Just Documentation of Dependencies)

Based on the research, implementing NIP-47 requires:

1. Implement NIP-44 encryption (prerequisite)
2. Create NIP-47 event models (kinds 13194, 23194, 23195, 23197)
3. Create request/response/notification data models
4. Create NIP-47 specific tags
5. Create connection URI parser
6. Create wallet service interface/abstraction
7. Create integration tests with Docker relay
8. Add NIP-47 to binary compatibility checks

## Conclusion

The Nostrino codebase has well-established patterns for implementing NIPs. Event models use a data class + `EventContent` interface pattern. Relay communication uses `RelayClient` with Flow-based message handling. Encryption currently uses NIP-04 (AES-CBC) via `CipherText` and `AesCipher`. **The primary blocker for NIP-47 is the missing NIP-44 encryption implementation**, which is required as the primary encryption method (with NIP-04 as fallback for legacy clients).
