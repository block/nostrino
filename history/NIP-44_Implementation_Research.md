# Research: NIP-44 Implementation Requirements for Nostrino

**Date**: December 17, 2025  
**Researcher**: Amp AI

## Research Question

What is required to implement NIP-44 (Encrypted Payloads v2) in the Nostrino SDK, and what is the current state of encryption support in the codebase?

## Summary

Nostrino currently implements NIP-04 encryption (AES-256-CBC) for encrypted direct messages. NIP-44 is a more secure encryption standard that uses ChaCha20, HKDF key derivation, HMAC-SHA256 authentication, and a custom padding scheme. This research documents:

1. The current NIP-04 implementation in Nostrino
2. The NIP-44 specification requirements
3. The cryptographic primitives needed for NIP-44
4. The existing infrastructure that can be reused
5. Implementation requirements and considerations

## Current State: NIP-04 Implementation

### Architecture

Nostrino uses a multiplatform architecture with `expect`/`actual` patterns for platform-specific crypto implementations:

- `commonMain/`: Platform-agnostic interfaces
- `jvmMain/`: JVM-specific implementations (fully functional)
- `nativeMain/`: Native implementations (currently TODO placeholders)

### NIP-04 Encryption Files

#### 1. Common Interface: AesCipher
**File**: [`lib/src/commonMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt)

Defines the platform-agnostic interface for AES encryption:
- `encrypt(plainText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray`
- `decrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): ByteArray`
- `generateIv(): ByteArray`

#### 2. JVM Implementation: AesCipher
**File**: [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt)

Uses Java's `javax.crypto` APIs:
- Algorithm: `AES/CBC/PKCS5Padding`
- Key size: 256 bits (32 bytes)
- IV size: 16 bytes
- Random IV generation using `SecureRandom`

#### 3. ECDH Key Exchange
**File**: [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt)

Uses `fr.acinq.secp256k1` library for:
- Public key derivation from private key
- ECDH shared secret calculation
- Returns raw 32-byte x-coordinate (unhashed)

**Key method**: `sharedSecret(privateKey, publicKey)` - returns 32-byte shared secret

#### 4. CipherText Container
**File**: [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt)

Data class holding encrypted content:
- `cipherText: ByteString` - the encrypted bytes
- `iv: ByteString` - initialization vector
- Format: `${ciphertext_base64}?iv=${iv_base64}`
- Includes `decipher()` method

#### 5. EncryptedDm Model
**File**: [`lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt)

Event kind 4 implementation:
- Extension function: `SecKey.encrypt(to: PubKey, plainText: String): CipherText`
- Uses ECDH shared secret as AES key directly (NIP-04 pattern)
- Generates random IV per message

#### 6. SecKey Integration
**File**: [`lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt:45-46)

The `SecKey` class provides:
- `sharedSecretWith(pub: PubKey): ByteArray` - ECDH shared secret calculation

### NIP-04 Test Coverage
**File**: [`lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt)

Uses Kotest property-based testing:
- Generates random key pairs and messages
- Tests encrypt → decrypt round-trip
- Validates decrypted message matches original

## NIP-44 Specification Requirements

### Algorithm: Version 2 (0x02)

NIP-44 v2 uses: **secp256k1 ECDH + HKDF + padding + ChaCha20 + HMAC-SHA256 + base64**

### Encryption Process (7 Steps)

#### 1. Calculate Conversation Key
- Perform ECDH: `shared_x = ECDH(privKey_A, pubKey_B)` (32-byte x-coordinate, unhashed)
- Apply HKDF-extract: `conversation_key = HKDF-extract(sha256, IKM=shared_x, salt="nip44-v2")`
- Symmetric: `conv(a, B) == conv(b, A)`

#### 2. Generate Random Nonce
- Generate 32 random bytes using CSPRNG
- Must be unique per message
- Never reuse nonces

#### 3. Derive Message Keys
- Input: `conversation_key` (32 bytes) + `nonce` (32 bytes)
- Apply HKDF-expand: `keys = HKDF-expand(sha256, PRK=conversation_key, info=nonce, L=76)`
- Slice output:
  - `chacha_key` = bytes [0:32]
  - `chacha_nonce` = bytes [32:44] (12 bytes)
  - `hmac_key` = bytes [44:76]

#### 4. Add Padding
- Format: `[length:u16_BE][plaintext][zeros]`
- Length range: 1-65535 bytes
- Padding algorithm: power-of-two based, minimum 32 bytes padded size
- Length encoded as big-endian u16 in first 2 bytes

#### 5. Encrypt with ChaCha20
- Algorithm: ChaCha20 (NOT XChaCha20)
- Key: `chacha_key` (32 bytes)
- Nonce: `chacha_nonce` (12 bytes)
- Counter: 0 (initial value)

#### 6. Calculate MAC
- Algorithm: HMAC-SHA256
- Key: `hmac_key`
- Message: `nonce || ciphertext` (AAD - Additional Authenticated Data)
- Output: 32-byte MAC

#### 7. Encode Payload
- Format: `version || nonce || ciphertext || mac`
- Version: 1 byte (0x02)
- Nonce: 32 bytes
- Ciphertext: variable length
- MAC: 32 bytes
- Encoding: Base64 with padding

**Total payload size range**: 132-87472 characters (base64), 99-65603 bytes (decoded)

### Decryption Process (7 Steps)

#### 1. Check Version Flag
- If first char is `#`: unsupported encoding version
- Otherwise: proceed with base64 decoding

#### 2. Decode Base64
- Validate length: 132-87472 chars
- Decode to bytes: 99-65603 bytes
- Parse: `version, nonce, ciphertext, mac`

#### 3. Calculate Conversation Key
- Same as encryption step 1

#### 4. Calculate Message Keys
- Same as encryption step 3

#### 5. Verify MAC
- Calculate MAC using AAD (`nonce || ciphertext`)
- Compare with decoded MAC using **constant-time** comparison
- Throw error if mismatch

#### 6. Decrypt with ChaCha20
- Same parameters as encryption step 5

#### 7. Remove Padding
- Read first 2 bytes as big-endian u16 for length
- Extract plaintext of that length
- Verify padding is all zeros

## Key Differences: NIP-04 vs NIP-44

| Aspect | NIP-04 | NIP-44 v2 |
|--------|--------|-----------|
| **Encryption** | AES-256-CBC | ChaCha20 |
| **Key Derivation** | Raw ECDH shared secret | HKDF-extract + HKDF-expand |
| **Authentication** | None | HMAC-SHA256 with AAD |
| **Padding** | PKCS5 | Custom power-of-two scheme |
| **Nonce Size** | 16 bytes (IV) | 32 bytes |
| **Encoding** | Custom (base64 + "?iv=") | Versioned base64 |
| **Security** | ❌ Malleable, no auth | ✅ Authenticated, resistant to multi-key attacks |
| **Forward Secrecy** | ❌ No | ❌ No (but better than NIP-04) |
| **Audited** | ❌ No | ✅ Yes (Cure53, Dec 2023) |

## Required Cryptographic Primitives

### 1. ChaCha20 Cipher ⚠️ NOT IMPLEMENTED

**Requirements**:
- Algorithm: ChaCha20 (RFC 8439)
- Key size: 32 bytes
- Nonce size: 12 bytes
- Initial counter: 0

**JVM Implementation Options**:
- Java 11+: `javax.crypto.Cipher.getInstance("ChaCha20")`
- Bouncy Castle: `org.bouncycastle.crypto.engines.ChaCha20Engine`

**Status**: ❌ Not implemented in Nostrino

### 2. HKDF (HMAC-based Key Derivation Function) ⚠️ NOT IMPLEMENTED

**Requirements**:
- Standard: RFC 5869
- Hash function: SHA-256
- Two phases:
  - `HKDF-extract(salt, IKM)` → PRK
  - `HKDF-expand(PRK, info, L)` → OKM

**JVM Implementation Options**:
- Java: No built-in (requires manual implementation or library)
- Bouncy Castle: `org.bouncycastle.crypto.generators.HKDFBytesGenerator`
- Google Tink: `com.google.crypto.tink.subtle.Hkdf`

**Status**: ❌ Not implemented in Nostrino

### 3. HMAC-SHA256 ⚠️ NOT IMPLEMENTED

**Requirements**:
- Algorithm: HMAC with SHA-256
- Key: 32 bytes (from HKDF)
- Message: nonce || ciphertext (AAD)
- Output: 32 bytes

**JVM Implementation**:
- Java built-in: `javax.crypto.Mac.getInstance("HmacSHA256")`

**Status**: ❌ Not implemented in Nostrino

### 4. Custom Padding Scheme ⚠️ NOT IMPLEMENTED

**Requirements**:
- Calculate padded length using power-of-two algorithm
- Format: `[u16_BE length][plaintext][zeros]`
- Min padded size: 32 bytes
- Max plaintext size: 65535 bytes

**Reference Implementation**:
```python
def calc_padded_len(unpadded_len):
    if unpadded_len < 1 or unpadded_len > 65535:
        raise ValueError("invalid plaintext length")
    
    next_power = 1 << (unpadded_len - 1).bit_length()
    chunk = next_power // 8 if next_power >= 256 else 32
    
    if unpadded_len <= 32:
        return 32
    else:
        return chunk * ((unpadded_len - 1) // chunk + 1)
```

**Status**: ❌ Not implemented in Nostrino

### 5. ECDH (secp256k1) ✅ IMPLEMENTED

**Current Implementation**:
- Library: `fr.acinq.secp256k1`
- File: [`EcdhProvider.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt)
- Returns: 32-byte unhashed x-coordinate

**Status**: ✅ Already implemented, can be reused

### 6. Secure Random ✅ IMPLEMENTED

**Current Implementation**:
- Uses `java.security.SecureRandom` for IV generation
- File: [`AesCipher.kt:37-42`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt#L37-L42)

**Status**: ✅ Already implemented, can be reused for 32-byte nonce generation

## Existing Infrastructure for Reuse

### ✅ Can Reuse

1. **ECDH Shared Secret Calculation**
   - [`EcdhProvider.sharedSecret()`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt#L26-30)
   - Returns unhashed 32-byte x-coordinate (required by NIP-44)

2. **Secure Random Number Generation**
   - Pattern from [`AesCipher.generateIv()`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt#L37-42)
   - Extend to generate 32-byte nonces

3. **Multiplatform Architecture**
   - `expect`/`actual` pattern for crypto primitives
   - Common interfaces in `commonMain/`
   - Platform implementations in `jvmMain/` and `nativeMain/`

4. **Base64 Encoding**
   - Likely available through Okio or Kotlin stdlib
   - Need to verify base64 with padding support

5. **ByteString Utilities**
   - Okio's `ByteString` used throughout codebase
   - Provides `.hex()`, `.base64()`, `.toByteArray()`

### ⚠️ Need New Implementation

1. **ChaCha20 Cipher**
   - No existing ChaCha20 implementation
   - Need JVM implementation (Java 11+ or Bouncy Castle)
   - Need native implementation (TODO)

2. **HKDF Key Derivation**
   - No existing HKDF implementation
   - Need to implement or add library dependency

3. **HMAC-SHA256**
   - No existing HMAC implementation
   - Can use `javax.crypto.Mac` on JVM

4. **NIP-44 Padding Scheme**
   - Custom algorithm unique to NIP-44
   - Must implement from specification

5. **Constant-Time Comparison**
   - Required for MAC verification
   - Need to implement or find library function

## Implementation Architecture

### Proposed File Structure

Following the existing NIP-04 pattern:

```
lib/src/commonMain/kotlin/app/cash/nostrino/crypto/
├── ChaCha20.kt                    # expect object for ChaCha20
├── Hkdf.kt                        # expect object for HKDF
├── HmacSha256.kt                  # expect object for HMAC-SHA256
├── Nip44Padding.kt                # Common padding logic
└── Nip44CipherText.kt             # NIP-44 ciphertext container

lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/
├── ChaCha20.kt                    # actual JVM implementation
├── Hkdf.kt                        # actual JVM implementation
└── HmacSha256.kt                  # actual JVM implementation

lib/src/nativeMain/kotlin/app/cash/nostrino/crypto/
├── ChaCha20.kt                    # actual Native implementation (TODO)
├── Hkdf.kt                        # actual Native implementation (TODO)
└── HmacSha256.kt                  # actual Native implementation (TODO)

lib/src/jvmMain/kotlin/app/cash/nostrino/model/
└── EncryptedDmV2.kt               # NIP-44 encrypted DM (or extend existing)

lib-test/src/test/kotlin/app/cash/nostrino/crypto/
├── ChaCha20Test.kt                # Unit tests
├── HkdfTest.kt                    # Unit tests
├── HmacSha256Test.kt              # Unit tests
├── Nip44PaddingTest.kt            # Unit tests
└── Nip44CipherTextTest.kt         # Integration tests

lib-test/src/test/kotlin/app/cash/nostrino/model/
└── EncryptedDmV2Test.kt           # End-to-end tests
```

### Extension Functions Pattern

Following the existing NIP-04 pattern in [`EncryptedDm.kt:48-52`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt#L48-L52):

```kotlin
// NIP-04 (existing)
fun SecKey.encrypt(to: PubKey, plainText: String): CipherText

// NIP-44 (proposed)
fun SecKey.encryptV2(to: PubKey, plainText: String): Nip44CipherText
```

## Dependencies Analysis

### Current Dependencies

**Crypto Libraries** (from [`gradle/libs.versions.toml`](file:///Users/jmawson/Development/sourcery/nostrino/gradle/libs.versions.toml)):

- **secp256k1**: `fr.acinq.secp256k1:secp256k1-kmp` v0.15.0
  - Used for: ECDH, public key derivation
  - Status: ✅ Sufficient for NIP-44 (ECDH is the same)

- **OkIO**: `com.squareup.okio:okio` v3.4.0
  - Used for: ByteString utilities, base64 encoding
  - Status: ✅ Can be reused for NIP-44

- **Secure Random**: `org.kotlincrypto:secure-random` v0.3.1
  - Used for: CSPRNG
  - Status: ✅ Can be reused for 32-byte nonce generation

### Missing Dependencies for NIP-44

The following cryptographic primitives are NOT available in current dependencies:

1. **ChaCha20 Cipher**
   - JVM Option 1: Use Java 11+ built-in `javax.crypto.Cipher.getInstance("ChaCha20")`
   - JVM Option 2: Add Bouncy Castle dependency
   - Recommendation: Use Java 11+ built-in (Nostrino already requires JVM 11)

2. **HKDF Implementation**
   - Not available in Java standard library
   - Options:
     - Implement from RFC 5869 (recommended, ~100 lines)
     - Add Google Tink: `com.google.crypto.tink:tink` (heavy dependency)
     - Add Bouncy Castle: `org.bouncycastle:bcprov-jdk15on` (large dependency)
   - Recommendation: Implement HKDF manually (lightweight, auditable)

3. **HMAC-SHA256**
   - Available in Java standard library: `javax.crypto.Mac.getInstance("HmacSHA256")`
   - Recommendation: Use built-in implementation

### Dependency Decision Summary

| Primitive | Solution | Requires New Dependency? |
|-----------|----------|-------------------------|
| ChaCha20 | Java 11+ built-in | ❌ No |
| HKDF | Manual implementation | ❌ No |
| HMAC-SHA256 | Java built-in | ❌ No |
| Padding | Manual implementation | ❌ No |

**Conclusion**: NIP-44 can be implemented without adding any new external dependencies.

## Test Vectors

The NIP-44 specification provides official test vectors for validation:

**Test Vector File**: `nip44.vectors.json`  
**SHA256 Checksum**: `269ed0f69e4c192512cc779e78c555090cebc7c785b609e338a62afc3ce25040`  
**Source**: https://github.com/paulmillr/nip44

### Test Vector Categories

1. **`valid.get_conversation_key`**: Calculate conversation_key from sec1 and pub2
2. **`valid.get_message_keys`**: Calculate chacha_key, chacha_nonce, hmac_key from conversation_key and nonce
3. **`valid.calc_padded_len`**: Padding length calculation
4. **`valid.encrypt_decrypt`**: Full encrypt/decrypt round-trip with intermediate values
5. **`valid.encrypt_decrypt_long_msg`**: Long message tests (with checksums)
6. **`invalid.encrypt_msg_lengths`**: Invalid message length handling
7. **`invalid.get_conversation_key`**: Invalid conversation key scenarios
8. **`invalid.decrypt`**: Invalid decryption scenarios

### Example Test Vector

```json
{
  "sec1": "0000000000000000000000000000000000000000000000000000000000000001",
  "sec2": "0000000000000000000000000000000000000000000000000000000000000002",
  "conversation_key": "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d",
  "nonce": "0000000000000000000000000000000000000000000000000000000000000001",
  "plaintext": "a",
  "payload": "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"
}
```

### Testing Strategy

Following Nostrino's existing test patterns:

1. **Unit Tests** (like [`CipherTextTest.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/crypto/CipherTextTest.kt)):
   - Test each primitive in isolation
   - Use official test vectors for validation

2. **Property-Based Tests** (like [`EncryptedDmTest.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt)):
   - Use Kotest's `checkAll` with `Arb` generators
   - Test encrypt → decrypt round-trips
   - Generate random keys and messages

3. **Integration Tests** (like [`RelayClientTest.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/client/RelayClientTest.kt)):
   - Test full message flow
   - Validate interoperability with reference implementations

## Implementation Considerations

### Security Considerations

1. **Constant-Time Operations**
   - MAC comparison MUST use constant-time algorithm
   - Prevents timing attacks
   - Use `java.security.MessageDigest.isEqual()` or implement manually

2. **Key Cleanup**
   - Derived keys should be zeroed after use
   - Prevents key material from lingering in memory
   - Pattern: Use `ByteArray.fill(0)` after encryption/decryption

3. **Nonce Uniqueness**
   - 32-byte nonce MUST be generated with CSPRNG
   - Never reuse nonces between messages
   - Current `SecureRandom` usage is sufficient

4. **Input Validation**
   - Validate all lengths before processing
   - Plaintext: 1-65535 bytes
   - Base64 payload: 132-87472 chars
   - Decoded payload: 99-65603 bytes

### Multiplatform Support

**JVM Platform**:
- ✅ All primitives available in Java 11+
- Can implement immediately

**Native Platform**:
- ⚠️ Currently has TODO placeholders
- Will need native crypto library (e.g., libsodium, OpenSSL)
- Or Kotlin/Native multiplatform crypto library
- Recommend: Defer native implementation until JVM is stable

### Backward Compatibility

**Coexistence with NIP-04**:
- NIP-44 should NOT replace NIP-04
- Both should be available
- Message format includes version byte (0x02) for NIP-44
- NIP-04 uses different format (`${base64}?iv=${base64}`)

**Proposed API**:
```kotlin
// NIP-04 (existing, unchanged)
fun SecKey.encrypt(to: PubKey, plainText: String): CipherText
fun CipherText.decipher(from: PubKey, to: SecKey): String

// NIP-44 (new)
fun SecKey.encryptV2(to: PubKey, plainText: String): Nip44CipherText
fun Nip44CipherText.decipher(from: PubKey, to: SecKey): String
```

### Performance Considerations

**ChaCha20 vs AES**:
- ChaCha20 is faster on platforms without AES hardware acceleration
- ChaCha20 is constant-time in software implementations
- ChaCha20 is better for multiplatform support

**HKDF Overhead**:
- Additional HMAC operations compared to NIP-04
- Conversation key can be cached (same for both directions)
- Per-message overhead is minimal (only message key derivation)

### Binary Compatibility

From [`AGENTS.md`](file:///Users/jmawson/Development/sourcery/nostrino/AGENTS.md#L74):
- Run `bin/gradle apiCheck` before committing
- Run `bin/gradle apiDump` after API changes
- New public APIs should be additive only

**NIP-44 Impact**:
- Adding new functions/classes is safe (additive)
- No changes to existing NIP-04 APIs
- Should not break binary compatibility

## Implementation Roadmap

### Phase 1: Core Primitives (JVM Only)

1. **Implement HKDF**
   - File: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Hkdf.kt`
   - Test with official test vectors

2. **Implement ChaCha20 wrapper**
   - File: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/ChaCha20.kt`
   - Use `javax.crypto.Cipher.getInstance("ChaCha20")`

3. **Implement HMAC-SHA256 wrapper**
   - File: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/HmacSha256.kt`
   - Use `javax.crypto.Mac.getInstance("HmacSHA256")`

4. **Implement NIP-44 padding**
   - File: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/Nip44Padding.kt`
   - Implement `calcPaddedLen()`, `pad()`, `unpad()`
   - Test with official test vectors

### Phase 2: NIP-44 Encryption

1. **Create Nip44CipherText**
   - File: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/Nip44CipherText.kt`
   - Parse/serialize versioned payload format
   - Include constant-time MAC comparison

2. **Implement conversation key derivation**
   - File: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44.kt`
   - `getConversationKey(sharedSecret: ByteArray): ByteArray`

3. **Implement message key derivation**
   - Same file as above
   - `getMessageKeys(conversationKey, nonce): Triple<ByteArray, ByteArray, ByteArray>`

4. **Implement encrypt/decrypt**
   - Same file as above
   - `encrypt(plaintext, conversationKey, nonce): Nip44CipherText`
   - `decrypt(ciphertext: Nip44CipherText, conversationKey): String`

### Phase 3: Integration

1. **Add SecKey extension functions**
   - File: Extend existing or new file
   - `fun SecKey.encryptV2(to: PubKey, plainText: String): Nip44CipherText`

2. **Consider event kind 44 or reuse kind 4**
   - Research Nostr ecosystem conventions
   - May need to coordinate with other Nostr implementations

### Phase 4: Testing

1. **Unit tests for all primitives**
   - Use official NIP-44 test vectors
   - Test edge cases and error conditions

2. **Property-based tests**
   - Encrypt/decrypt round-trips
   - Random key pairs and messages
   - Follow pattern from `EncryptedDmTest.kt`

3. **Integration tests**
   - Test with other NIP-44 implementations
   - Verify interoperability

### Phase 5: Native Support (Future)

1. **Research native crypto options**
   - Kotlin Multiplatform libraries
   - Native bindings (libsodium, OpenSSL)

2. **Implement native primitives**
   - Mirror JVM implementation
   - May require different underlying libraries

3. **Test on all platforms**
   - JVM, iOS, Linux

## Reference Implementations

### Official Implementations (Audited)

From https://github.com/paulmillr/nip44:

| Language | Repository | License | Audit Status |
|----------|------------|---------|--------------|
| TypeScript/JS | nostr-protocol/nips | Public Domain | ✅ Audited |
| Go | ekzyis/nip44 | MIT | ✅ Audited |
| Rust | mikedilger/nip44 | MIT | ✅ Audited |

### Other Implementations (Not Audited)

| Language | Repository | License |
|----------|------------|---------|
| C | vnuge/noscrypt | LGPL 2.1+ |
| Dart | chebizarro/dart-nip44 | MIT |
| F# | lontivero/Nostra | GPL 2 |
| Haskell | futrnostr/futr | GPL 3 |
| Kotlin | vitorpamplona/amethyst | MIT |
| Swift | nostr-sdk/nostr-sdk-ios | MIT |

**Note**: The Kotlin implementation in Amethyst could serve as a reference, but it should be validated against test vectors.

## Code References

### Files Reviewed

1. [`lib/src/commonMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt) - AES interface
2. [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt) - AES JVM implementation
3. [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt) - ECDH shared secret
4. [`lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt) - NIP-04 ciphertext
5. [`lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt) - NIP-04 encrypted DM
6. [`lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/commonMain/kotlin/app/cash/nostrino/crypto/SecKey.kt) - Secret key with ECDH
7. [`lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt`](file:///Users/jmawson/Development/sourcery/nostrino/lib-test/src/test/kotlin/app/cash/nostrino/model/EncryptedDmTest.kt) - NIP-04 tests
8. [`gradle/libs.versions.toml`](file:///Users/jmawson/Development/sourcery/nostrino/gradle/libs.versions.toml) - Dependencies

## Related Research

- NIP-44 Specification: https://github.com/nostr-protocol/nips/blob/master/44.md
- NIP-44 Test Vectors: https://github.com/paulmillr/nip44
- Cure53 Security Audit: https://cure53.de/audit-report_nip44-implementations.pdf
- RFC 5869 (HKDF): https://datatracker.ietf.org/doc/html/rfc5869
- RFC 8439 (ChaCha20): https://datatracker.ietf.org/doc/html/rfc8439

## Summary of Findings

### Current State
- ✅ NIP-04 (AES-256-CBC) is fully implemented on JVM
- ✅ ECDH key exchange infrastructure exists and can be reused
- ✅ Multiplatform architecture is in place
- ❌ NIP-44 (ChaCha20 + HKDF + HMAC) is not implemented

### Implementation Feasibility
- ✅ Can be implemented **without** adding external dependencies
- ✅ Java 11+ provides ChaCha20 and HMAC-SHA256
- ✅ HKDF can be implemented manually (~100 lines)
- ✅ Existing ECDH infrastructure can be reused
- ⚠️ Native platform support will require additional work

### Security & Quality
- ✅ Specification is well-defined and audited (Cure53, Dec 2023)
- ✅ Official test vectors available for validation
- ✅ Reference implementations exist in multiple languages
- ✅ Addresses NIP-04 security weaknesses (adds authentication, prevents malleability)

### Recommended Approach
1. Implement JVM version first with test vectors
2. Follow existing multiplatform patterns
3. Maintain backward compatibility with NIP-04
4. Defer native implementation until JVM is stable and tested

## Open Questions

1. **Event Kind**: Should NIP-44 encrypted messages use a new event kind (e.g., kind 44), or reuse kind 4 with version detection?
2. **API Naming**: Should the new functions be named `encryptV2`/`decryptV2`, or `encryptNip44`/`decryptNip44`?
3. **Conversation Key Caching**: Should the conversation key be cached to avoid repeated HKDF-extract operations?
4. **Native Priority**: What is the timeline/priority for native platform support?
5. **Deprecation Path**: Should NIP-04 be deprecated once NIP-44 is implemented, or maintained indefinitely?

---

**End of Research Document**
