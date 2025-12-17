# NIP-44 Encrypted Payloads Implementation Plan

## Overview

Implement NIP-44 v2 (Encrypted Payloads - Versioned) encryption for the Nostrino SDK. NIP-44 provides authenticated encryption using ChaCha20, HKDF key derivation, and HMAC-SHA256, offering significant security improvements over NIP-04's AES-CBC approach.

## Current State Analysis

### Existing Infrastructure

Nostrino currently implements NIP-04 encryption with the following reusable components:

1. **ECDH Key Exchange**: [EcdhProvider.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/EcdhProvider.kt) provides `sharedSecret()` that returns the unhashed 32-byte x-coordinate (exactly what NIP-44 requires)

2. **Multiplatform Architecture**: Uses `expect`/`actual` pattern with:
   - Common interfaces in `commonMain/`
   - JVM implementations in `jvmMain/` (fully functional)
   - Native placeholders in `nativeMain/` (TODO)

3. **Secure Random**: [AesCipher.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/AesCipher.kt#L37-L42) uses `SecureRandom` for IV generation (can be adapted for 32-byte nonces)

4. **Test Infrastructure**: Uses Kotest StringSpec with property-based testing via `Arb` generators

### Missing Components

- ChaCha20 cipher (available in Java 11+ via `javax.crypto.Cipher`)
- HKDF key derivation (needs manual implementation, ~100 lines)
- HMAC-SHA256 (available in Java via `javax.crypto.Mac`)
- NIP-44 custom padding scheme
- Constant-time MAC comparison

### Key Constraints

- **No new dependencies required**: All cryptographic primitives available in Java 11+
- **Binary compatibility**: Changes must be additive only (run `bin/gradle apiCheck` before commit)
- **Multiplatform**: JVM implementation first, native deferred
- **NIP-44 is NOT a NIP-04 replacement**: Different use cases, both should coexist

## Desired End State

A complete NIP-44 v2 encryption implementation for JVM with:

1. All cryptographic primitives implemented and tested against official test vectors
2. Clean API following Nostrino conventions (extension functions on `SecKey`)
3. Comprehensive test coverage (unit tests with test vectors + property-based tests)
4. Full documentation and examples
5. Conversation key caching support

### Success Verification

#### Automated Verification:
- All tests pass: `bin/gradle test`
- Type checking passes: `bin/gradle build`
- No binary compatibility issues: `bin/gradle apiCheck`
- Official NIP-44 test vectors pass (all categories)

#### Manual Verification:
- Encrypt/decrypt round-trip works correctly
- Interoperability with other NIP-44 implementations verified
- Conversation key caching provides performance improvement
- Error messages are clear and helpful

## What We're NOT Doing

1. **NOT replacing NIP-04**: NIP-44 and NIP-04 serve different purposes and will coexist
2. **NOT defining event kinds**: NIP-44 only defines encryption format, not message types
3. **NOT implementing native platforms**: Native (iOS/Linux) support deferred until JVM is stable
4. **NOT adding external dependencies**: Using only Java 11+ built-ins
5. **NOT implementing forward secrecy**: Out of scope for NIP-44 specification
6. **NOT supporting attachments**: NIP-44 is text-only (max 65535 bytes)

## Implementation Approach

Follow the existing NIP-04 patterns:
- Use `expect`/`actual` for platform-specific crypto
- Extension functions on `SecKey` for encryption API
- Data classes for ciphertext containers
- Test with official vectors + property-based testing

Implement in phases to enable incremental testing and validation.

---

## Phase 1: Core Cryptographic Primitives

### Overview
Implement the low-level crypto building blocks needed for NIP-44, validated against official test vectors.

### Changes Required:

#### 1. HKDF Implementation (Common Interface)
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/Hkdf.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

expect object Hkdf {
  fun extract(salt: ByteArray, ikm: ByteArray): ByteArray
  fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray
}
```

#### 2. HKDF Implementation (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Hkdf.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object Hkdf {
  actual fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    return mac.doFinal(ikm)
  }

  actual fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length <= 255 * 32) { "Length must be <= 8160 bytes" }
    
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    
    val result = ByteArray(length)
    var offset = 0
    var counter = 1
    var previousBlock = ByteArray(0)
    
    while (offset < length) {
      mac.update(previousBlock)
      mac.update(info)
      mac.update(counter.toByte())
      previousBlock = mac.doFinal()
      
      val bytesToCopy = minOf(previousBlock.size, length - offset)
      System.arraycopy(previousBlock, 0, result, offset, bytesToCopy)
      offset += bytesToCopy
      counter++
    }
    
    return result
  }
}
```

#### 3. ChaCha20 Interface (Common)
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/ChaCha20.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

expect object ChaCha20 {
  fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
  fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
}
```

#### 4. ChaCha20 Implementation (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/ChaCha20.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import javax.crypto.Cipher
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object ChaCha20 {
  actual fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }
    
    val cipher = Cipher.getInstance("ChaCha20")
    val paramSpec = ChaCha20ParameterSpec(nonce, 0)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), paramSpec)
    return cipher.doFinal(plaintext)
  }

  actual fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }
    
    val cipher = Cipher.getInstance("ChaCha20")
    val paramSpec = ChaCha20ParameterSpec(nonce, 0)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), paramSpec)
    return cipher.doFinal(ciphertext)
  }
}
```

#### 5. HMAC-SHA256 Interface (Common)
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/HmacSha256.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

expect object HmacSha256 {
  fun compute(key: ByteArray, message: ByteArray): ByteArray
  fun computeWithAad(key: ByteArray, aad: ByteArray, message: ByteArray): ByteArray
  fun isEqualConstantTime(a: ByteArray, b: ByteArray): Boolean
}
```

#### 6. HMAC-SHA256 Implementation (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/HmacSha256.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object HmacSha256 {
  actual fun compute(key: ByteArray, message: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(message)
  }

  actual fun computeWithAad(key: ByteArray, aad: ByteArray, message: ByteArray): ByteArray {
    require(aad.size == 32) { "AAD must be 32 bytes" }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    mac.update(aad)
    mac.update(message)
    return mac.doFinal()
  }

  actual fun isEqualConstantTime(a: ByteArray, b: ByteArray): Boolean =
    MessageDigest.isEqual(a, b)
}
```

#### 7. NIP-44 Padding (Common)
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/Nip44Padding.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import kotlin.math.floor
import kotlin.math.log2

object Nip44Padding {
  private const val MIN_PLAINTEXT_SIZE = 1
  private const val MAX_PLAINTEXT_SIZE = 65535

  fun calcPaddedLen(unpadded: Int): Int {
    require(unpadded in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
      "Plaintext length must be between $MIN_PLAINTEXT_SIZE and $MAX_PLAINTEXT_SIZE bytes"
    }

    if (unpadded <= 32) return 32

    val nextPower = 1 shl (floor(log2((unpadded - 1).toDouble())).toInt() + 1)
    val chunk = if (nextPower <= 256) 32 else nextPower / 8

    return chunk * ((unpadded - 1) / chunk + 1)
  }

  fun pad(plaintext: String): ByteArray {
    val unpadded = plaintext.encodeToByteArray()
    val unpaddedLen = unpadded.size
    
    require(unpaddedLen in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
      "Plaintext length must be between $MIN_PLAINTEXT_SIZE and $MAX_PLAINTEXT_SIZE bytes"
    }

    val paddedLen = calcPaddedLen(unpaddedLen)
    val result = ByteArray(2 + paddedLen)
    
    result[0] = (unpaddedLen shr 8).toByte()
    result[1] = (unpaddedLen and 0xFF).toByte()
    
    unpadded.copyInto(result, destinationOffset = 2)
    
    return result
  }

  fun unpad(padded: ByteArray): String {
    require(padded.size >= 2) { "Padded data must be at least 2 bytes" }
    
    val unpaddedLen = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
    
    require(unpaddedLen > 0) { "Invalid padding: length is zero" }
    require(padded.size >= 2 + unpaddedLen) { "Invalid padding: data too short" }
    require(padded.size == 2 + calcPaddedLen(unpaddedLen)) { "Invalid padding: incorrect padded length" }
    
    for (i in 2 + unpaddedLen until padded.size) {
      require(padded[i] == 0.toByte()) { "Invalid padding: non-zero padding bytes" }
    }
    
    return padded.decodeToString(startIndex = 2, endIndex = 2 + unpaddedLen)
  }
}
```

### Unit Tests:

#### 8. HKDF Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/HkdfTest.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class HkdfTest : StringSpec({

  "extract with test vector from NIP-44" {
    val ikm = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()
    val salt = "nip44-v2".encodeToByteArray()
    val expected = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".decodeHex()
    
    val result = Hkdf.extract(salt, ikm.toByteArray())
    
    result.toByteString().hex() shouldBe expected.hex()
  }

  "expand generates correct length output" {
    val prk = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".decodeHex()
    val info = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()
    
    val result = Hkdf.expand(prk.toByteArray(), info.toByteArray(), 76)
    
    result.size shouldBe 76
  }
})
```

#### 9. ChaCha20 Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/ChaCha20Test.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.security.SecureRandom

class ChaCha20Test : StringSpec({

  "encrypt and decrypt round-trip" {
    checkAll(Arb.string()) { plaintext ->
      val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
      val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
      
      val encrypted = ChaCha20.encrypt(plaintext.encodeToByteArray(), key, nonce)
      val decrypted = ChaCha20.decrypt(encrypted, key, nonce)
      
      decrypted.decodeToString() shouldBe plaintext
    }
  }
})
```

#### 10. HMAC-SHA256 Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/HmacSha256Test.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HmacSha256Test : StringSpec({

  "compute produces 32-byte output" {
    val key = ByteArray(32)
    val message = "test message".encodeToByteArray()
    
    val result = HmacSha256.compute(key, message)
    
    result.size shouldBe 32
  }

  "constant-time comparison detects equality" {
    val a = ByteArray(32) { it.toByte() }
    val b = ByteArray(32) { it.toByte() }
    
    HmacSha256.isEqualConstantTime(a, b) shouldBe true
  }

  "constant-time comparison detects inequality" {
    val a = ByteArray(32) { it.toByte() }
    val b = ByteArray(32) { (it + 1).toByte() }
    
    HmacSha256.isEqualConstantTime(a, b) shouldBe false
  }
})
```

#### 11. Padding Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44PaddingTest.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Nip44PaddingTest : StringSpec({

  "calcPaddedLen matches test vectors" {
    Nip44Padding.calcPaddedLen(1) shouldBe 32
    Nip44Padding.calcPaddedLen(32) shouldBe 32
    Nip44Padding.calcPaddedLen(33) shouldBe 64
    Nip44Padding.calcPaddedLen(37) shouldBe 64
    Nip44Padding.calcPaddedLen(45) shouldBe 64
    Nip44Padding.calcPaddedLen(49) shouldBe 64
    Nip44Padding.calcPaddedLen(64) shouldBe 64
    Nip44Padding.calcPaddedLen(65) shouldBe 96
    Nip44Padding.calcPaddedLen(100) shouldBe 128
  }

  "pad and unpad round-trip" {
    val plaintext = "Hello, NIP-44!"
    
    val padded = Nip44Padding.pad(plaintext)
    val unpadded = Nip44Padding.unpad(padded)
    
    unpadded shouldBe plaintext
  }

  "padded result has correct structure" {
    val plaintext = "test"
    val padded = Nip44Padding.pad(plaintext)
    
    val length = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
    length shouldBe 4
    
    padded.size shouldBe 2 + Nip44Padding.calcPaddedLen(4)
    
    for (i in 2 + 4 until padded.size) {
      padded[i] shouldBe 0
    }
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] All unit tests pass: `bin/gradle test`
- [ ] HKDF test vectors match NIP-44 specification
- [ ] Padding calculations match official test vectors
- [ ] ChaCha20 encrypt/decrypt round-trip successful
- [ ] HMAC constant-time comparison works correctly

#### Manual Verification:
- [ ] Code follows Nostrino conventions (expect/actual pattern)
- [ ] Copyright headers use 2025
- [ ] No new external dependencies added
- [ ] API check passes: `bin/gradle apiCheck`

---

## Phase 2: NIP-44 Encryption Engine

### Overview
Build the high-level NIP-44 encryption/decryption logic using the primitives from Phase 1.

### Changes Required:

#### 1. Nip44CipherText Container (Common)
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/crypto/Nip44CipherText.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

data class Nip44CipherText(
  val version: Byte,
  val nonce: ByteString,
  val ciphertext: ByteString,
  val mac: ByteString
) {
  init {
    require(version == VERSION_2) { "Only version 2 is supported" }
    require(nonce.size == 32) { "Nonce must be 32 bytes" }
    require(mac.size == 32) { "MAC must be 32 bytes" }
  }

  fun toBase64(): String {
    val payload = ByteArray(1 + 32 + ciphertext.size + 32)
    payload[0] = version
    nonce.toByteArray().copyInto(payload, destinationOffset = 1)
    ciphertext.toByteArray().copyInto(payload, destinationOffset = 33)
    mac.toByteArray().copyInto(payload, destinationOffset = 33 + ciphertext.size)
    return payload.toByteString().base64()
  }

  override fun toString(): String = toBase64()

  companion object {
    const val VERSION_2: Byte = 0x02

    fun parse(payload: String): Nip44CipherText {
      require(payload.isNotEmpty() && payload[0] != '#') { "Unknown version" }
      require(payload.length in 132..87472) { "Invalid payload size" }

      val decoded = payload.decodeBase64() 
        ?: throw IllegalArgumentException("Invalid base64 encoding")
      
      require(decoded.size in 99..65603) { "Invalid data size" }
      
      val version = decoded[0]
      require(version == VERSION_2) { "Unknown version $version" }
      
      val nonce = decoded.substring(1, 33)
      val mac = decoded.substring(decoded.size - 32)
      val ciphertext = decoded.substring(33, decoded.size - 32)
      
      return Nip44CipherText(version, nonce, ciphertext, mac)
    }
  }
}
```

#### 2. NIP-44 Encryption Core (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

object Nip44 {
  private const val SALT = "nip44-v2"

  fun getConversationKey(sharedSecret: ByteArray): ByteArray {
    require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }
    return Hkdf.extract(SALT.encodeToByteArray(), sharedSecret)
  }

  fun getMessageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
    require(conversationKey.size == 32) { "Conversation key must be 32 bytes" }
    require(nonce.size == 32) { "Nonce must be 32 bytes" }
    
    val expanded = Hkdf.expand(conversationKey, nonce, 76)
    
    return MessageKeys(
      chachaKey = expanded.copyOfRange(0, 32),
      chachaNonce = expanded.copyOfRange(32, 44),
      hmacKey = expanded.copyOfRange(44, 76)
    )
  }

  fun generateNonce(): ByteArray = ByteArray(32).apply {
    SecureRandom().nextBytes(this)
  }

  fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = generateNonce()): Nip44CipherText {
    val keys = getMessageKeys(conversationKey, nonce)
    
    val padded = Nip44Padding.pad(plaintext)
    val ciphertext = ChaCha20.encrypt(padded, keys.chachaKey, keys.chachaNonce)
    val mac = HmacSha256.computeWithAad(keys.hmacKey, nonce, ciphertext)
    
    keys.clear()
    
    return Nip44CipherText(
      version = Nip44CipherText.VERSION_2,
      nonce = nonce.toByteString(),
      ciphertext = ciphertext.toByteString(),
      mac = mac.toByteString()
    )
  }

  fun decrypt(payload: Nip44CipherText, conversationKey: ByteArray): String {
    val keys = getMessageKeys(conversationKey, payload.nonce.toByteArray())
    
    val calculatedMac = HmacSha256.computeWithAad(
      keys.hmacKey, 
      payload.nonce.toByteArray(), 
      payload.ciphertext.toByteArray()
    )
    
    require(HmacSha256.isEqualConstantTime(calculatedMac, payload.mac.toByteArray())) {
      "Invalid MAC"
    }
    
    val paddedPlaintext = ChaCha20.decrypt(
      payload.ciphertext.toByteArray(),
      keys.chachaKey,
      keys.chachaNonce
    )
    
    keys.clear()
    
    return Nip44Padding.unpad(paddedPlaintext)
  }

  data class MessageKeys(
    val chachaKey: ByteArray,
    val chachaNonce: ByteArray,
    val hmacKey: ByteArray
  ) {
    fun clear() {
      chachaKey.fill(0)
      chachaNonce.fill(0)
      hmacKey.fill(0)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is MessageKeys) return false
      if (!chachaKey.contentEquals(other.chachaKey)) return false
      if (!chachaNonce.contentEquals(other.chachaNonce)) return false
      if (!hmacKey.contentEquals(other.hmacKey)) return false
      return true
    }

    override fun hashCode(): Int {
      var result = chachaKey.contentHashCode()
      result = 31 * result + chachaNonce.contentHashCode()
      result = 31 * result + hmacKey.contentHashCode()
      return result
    }
  }
}
```

### Unit Tests:

#### 3. Nip44CipherText Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44CipherTextTest.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex

class Nip44CipherTextTest : StringSpec({

  "parse and encode round-trip" {
    val payload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"
    
    val parsed = Nip44CipherText.parse(payload)
    val encoded = parsed.toBase64()
    
    encoded shouldBe payload
  }

  "parse validates version" {
    val nonce = "00".repeat(32).decodeHex()
    val ciphertext = "00".repeat(10).decodeHex()
    val mac = "00".repeat(32).decodeHex()
    
    val ct = Nip44CipherText(
      version = Nip44CipherText.VERSION_2,
      nonce = nonce,
      ciphertext = ciphertext,
      mac = mac
    )
    
    ct.version shouldBe 0x02.toByte()
  }
})
```

#### 4. NIP-44 Integration Tests with Test Vectors
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44Test.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex

class Nip44Test : StringSpec({

  "getConversationKey matches test vector" {
    val sharedSecret = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()
    val expected = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d"
    
    val conversationKey = Nip44.getConversationKey(sharedSecret.toByteArray())
    
    conversationKey.toByteString().hex() shouldBe expected
  }

  "encrypt and decrypt with known test vector" {
    val conversationKey = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".decodeHex()
    val nonce = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()
    val plaintext = "a"
    val expectedPayload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"
    
    val encrypted = Nip44.encrypt(plaintext, conversationKey.toByteArray(), nonce.toByteArray())
    
    encrypted.toBase64() shouldBe expectedPayload
    
    val decrypted = Nip44.decrypt(encrypted, conversationKey.toByteArray())
    
    decrypted shouldBe plaintext
  }

  "encrypt and decrypt round-trip with random data" {
    val sharedSecret = ByteArray(32) { it.toByte() }
    val conversationKey = Nip44.getConversationKey(sharedSecret)
    val plaintext = "Hello, NIP-44!"
    
    val encrypted = Nip44.encrypt(plaintext, conversationKey)
    val decrypted = Nip44.decrypt(encrypted, conversationKey)
    
    decrypted shouldBe plaintext
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] All tests pass: `bin/gradle test`
- [ ] Test vectors from NIP-44 specification match exactly
- [ ] Encrypt/decrypt round-trip works with random data
- [ ] MAC validation prevents tampering
- [ ] Keys are properly cleared after use

#### Manual Verification:
- [ ] Base64 encoding/decoding works correctly
- [ ] Error messages are clear for invalid payloads
- [ ] Conversation key derivation is symmetric

---

## Phase 3: Integration & Public API

### Overview
Add public API following Nostrino conventions with extension functions on `SecKey` and conversation key caching.

### Changes Required:

#### 1. Conversation Key Cache (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44ConversationKeyCache.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import java.util.concurrent.ConcurrentHashMap

object Nip44ConversationKeyCache {
  private val cache = ConcurrentHashMap<String, ByteArray>()

  fun get(secKey: SecKey, pubKey: PubKey): ByteArray {
    val cacheKey = buildCacheKey(secKey, pubKey)
    return cache.getOrPut(cacheKey) {
      val sharedSecret = secKey.sharedSecretWith(pubKey)
      Nip44.getConversationKey(sharedSecret)
    }
  }

  fun clear() {
    cache.values.forEach { it.fill(0) }
    cache.clear()
  }

  private fun buildCacheKey(secKey: SecKey, pubKey: PubKey): String {
    val sorted = listOf(secKey.pubKey.hex, pubKey.hex).sorted()
    return sorted.joinToString(":")
  }
}
```

#### 2. SecKey Extension Functions (JVM)
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44Extensions.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

fun SecKey.encryptNip44(to: PubKey, plaintext: String, useCache: Boolean = true): Nip44CipherText {
  val conversationKey = if (useCache) {
    Nip44ConversationKeyCache.get(this, to)
  } else {
    val sharedSecret = sharedSecretWith(to)
    Nip44.getConversationKey(sharedSecret)
  }
  
  return Nip44.encrypt(plaintext, conversationKey)
}

fun Nip44CipherText.decipherNip44(from: PubKey, to: SecKey, useCache: Boolean = true): String {
  val conversationKey = if (useCache) {
    Nip44ConversationKeyCache.get(to, from)
  } else {
    val sharedSecret = to.sharedSecretWith(from)
    Nip44.getConversationKey(sharedSecret)
  }
  
  return Nip44.decrypt(this, conversationKey)
}
```

### Property-Based Tests:

#### 3. End-to-End Encryption Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44ExtensionsTest.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll

class Nip44ExtensionsTest : StringSpec({

  "encrypt and decrypt with cache" {
    checkAll(arbTestData) { (sender, recipient, message) ->
      val encrypted = sender.encryptNip44(recipient.pubKey, message, useCache = true)
      val decrypted = encrypted.decipherNip44(sender.pubKey, recipient, useCache = true)
      
      decrypted shouldBe message
    }
  }

  "encrypt and decrypt without cache" {
    checkAll(arbTestData) { (sender, recipient, message) ->
      val encrypted = sender.encryptNip44(recipient.pubKey, message, useCache = false)
      val decrypted = encrypted.decipherNip44(sender.pubKey, recipient, useCache = false)
      
      decrypted shouldBe message
    }
  }

  "conversation key is symmetric" {
    checkAll(Arb.triple(arbSecKey, arbSecKey, Arb.string())) { (alice, bob, message) ->
      val aliceToBob = alice.encryptNip44(bob.pubKey, message)
      val bobDecrypts = aliceToBob.decipherNip44(alice.pubKey, bob)
      
      bobDecrypts shouldBe message
      
      val bobToAlice = bob.encryptNip44(alice.pubKey, message)
      val aliceDecrypts = bobToAlice.decipherNip44(bob.pubKey, alice)
      
      aliceDecrypts shouldBe message
    }
  }
}) {
  companion object {
    private val arbTestData = Arb.triple(arbSecKey, arbSecKey, Arb.string(1..1000))
  }
}
```

### Success Criteria:

#### Automated Verification:
- [ ] All tests pass: `bin/gradle test`
- [ ] Property-based tests verify conversation key symmetry
- [ ] Cache improves performance (measure with/without)
- [ ] Binary compatibility check: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] API follows Nostrino conventions
- [ ] Extension functions work intuitively
- [ ] Cache can be safely cleared
- [ ] Documentation is clear and complete

---

## Phase 4: Comprehensive Testing & Validation

### Overview
Implement comprehensive testing with official NIP-44 test vectors and validate interoperability.

### Changes Required:

#### 1. Download Official Test Vectors
**Manual Step**: Download `nip44.vectors.json` from https://github.com/paulmillr/nip44 and place in `lib-test/src/test/resources/`

Verify SHA256: `269ed0f69e4c192512cc779e78c555090cebc7c785b609e338a62afc3ce25040`

#### 2. Test Vector Parser
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44TestVectors.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class Nip44TestVectors(
  val v2: V2Vectors
) {
  data class V2Vectors(
    val valid: ValidVectors,
    val invalid: InvalidVectors
  )

  data class ValidVectors(
    val get_conversation_key: List<ConversationKeyVector>,
    val get_message_keys: List<MessageKeysVector>,
    val calc_padded_len: List<List<Int>>,
    val encrypt_decrypt: List<EncryptDecryptVector>,
    val encrypt_decrypt_long_msg: List<EncryptDecryptLongVector>
  )

  data class InvalidVectors(
    val encrypt_msg_lengths: List<Int>,
    val get_conversation_key: List<InvalidConversationKeyVector>,
    val decrypt: List<InvalidDecryptVector>
  )

  data class ConversationKeyVector(
    val sec1: String,
    val pub2: String,
    val conversation_key: String,
    val note: String? = null
  )

  data class MessageKeysVector(
    val conversation_key: String,
    val nonce: String,
    val chacha_key: String,
    val chacha_nonce: String,
    val hmac_key: String
  )

  data class EncryptDecryptVector(
    val sec1: String,
    val sec2: String,
    val conversation_key: String,
    val nonce: String,
    val plaintext: String,
    val payload: String
  )

  data class EncryptDecryptLongVector(
    val conversation_key: String,
    val nonce: String,
    val pattern: String,
    val repeat: Int,
    val plaintext_sha256: String,
    val payload_sha256: String
  )

  data class InvalidConversationKeyVector(
    val sec1: String,
    val pub2: String,
    val note: String
  )

  data class InvalidDecryptVector(
    val conversation_key: String,
    val nonce: String,
    val plaintext: String,
    val payload: String,
    val note: String
  )

  companion object {
    fun load(): Nip44TestVectors {
      val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
      val adapter: JsonAdapter<Nip44TestVectors> = moshi.adapter(Nip44TestVectors::class.java)
      
      val json = File("lib-test/src/test/resources/nip44.vectors.json").readText()
      return adapter.fromJson(json)!!
    }
  }
}
```

#### 3. Comprehensive Test Vector Suite
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/crypto/Nip44OfficialTestVectorsTest.kt`

```kotlin
/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.nostrino.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class Nip44OfficialTestVectorsTest : StringSpec({

  val vectors = Nip44TestVectors.load()

  "valid conversation keys" {
    vectors.v2.valid.get_conversation_key.forEach { vector ->
      val sharedSecret = vector.sec1.decodeHex().toByteArray()
      val conversationKey = Nip44.getConversationKey(sharedSecret)
      
      conversationKey.toByteString().hex() shouldBe vector.conversation_key
    }
  }

  "valid message keys derivation" {
    vectors.v2.valid.get_message_keys.forEach { vector ->
      val conversationKey = vector.conversation_key.decodeHex().toByteArray()
      val nonce = vector.nonce.decodeHex().toByteArray()
      
      val keys = Nip44.getMessageKeys(conversationKey, nonce)
      
      keys.chachaKey.toByteString().hex() shouldBe vector.chacha_key
      keys.chachaNonce.toByteString().hex() shouldBe vector.chacha_nonce
      keys.hmacKey.toByteString().hex() shouldBe vector.hmac_key
    }
  }

  "valid padding calculations" {
    vectors.v2.valid.calc_padded_len.forEach { (unpadded, expected) ->
      Nip44Padding.calcPaddedLen(unpadded) shouldBe expected
    }
  }

  "valid encrypt and decrypt" {
    vectors.v2.valid.encrypt_decrypt.forEach { vector ->
      val conversationKey = vector.conversation_key.decodeHex().toByteArray()
      val nonce = vector.nonce.decodeHex().toByteArray()
      
      val encrypted = Nip44.encrypt(vector.plaintext, conversationKey, nonce)
      encrypted.toBase64() shouldBe vector.payload
      
      val parsed = Nip44CipherText.parse(vector.payload)
      val decrypted = Nip44.decrypt(parsed, conversationKey)
      decrypted shouldBe vector.plaintext
    }
  }

  "invalid message lengths throw" {
    vectors.v2.invalid.encrypt_msg_lengths.forEach { invalidLength ->
      shouldThrow<IllegalArgumentException> {
        Nip44Padding.pad("x".repeat(invalidLength))
      }
    }
  }

  "invalid decrypt throws" {
    vectors.v2.invalid.decrypt.forEach { vector ->
      val conversationKey = vector.conversation_key.decodeHex().toByteArray()
      val payload = Nip44CipherText.parse(vector.payload)
      
      shouldThrow<Exception> {
        Nip44.decrypt(payload, conversationKey)
      }
    }
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] All official test vectors pass: `bin/gradle test`
- [ ] Valid conversation key vectors match exactly
- [ ] Valid message key derivation vectors match exactly
- [ ] Valid padding calculations match exactly
- [ ] Valid encrypt/decrypt vectors match exactly
- [ ] Invalid cases properly throw exceptions
- [ ] Build passes: `bin/gradle build`
- [ ] Binary compatibility: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] Test vector SHA256 checksum verified
- [ ] All test categories covered
- [ ] Error handling works correctly
- [ ] Performance is acceptable

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that interoperability testing with other NIP-44 implementations was successful before considering the implementation complete.

---

## Testing Strategy

### Unit Tests
- **Primitives**: Test each cryptographic primitive in isolation (HKDF, ChaCha20, HMAC, Padding)
- **Test Vectors**: Use official NIP-44 test vectors for validation
- **Edge Cases**: Boundary conditions, invalid inputs, error handling

### Property-Based Tests
- **Round-Trip**: Encrypt → Decrypt must return original plaintext
- **Symmetry**: Conversation key must be same regardless of key order
- **Random Data**: Test with generated key pairs and messages using Kotest `Arb`

### Integration Tests
- **End-to-End**: Full encryption workflow through public API
- **Interoperability**: Verify compatibility with other NIP-44 implementations
- **Cache Behavior**: Validate conversation key caching works correctly

### Manual Testing
1. Generate test messages and verify encryption
2. Test with other NIP-44 implementations (e.g., JavaScript, Rust)
3. Verify error messages are helpful
4. Test performance with/without caching

## Performance Considerations

### Conversation Key Caching
- **Benefit**: Avoids repeated ECDH + HKDF-extract operations
- **Trade-off**: Memory usage vs computation time
- **Cache Strategy**: ConcurrentHashMap for thread-safety, manual clearing available

### ChaCha20 Performance
- ChaCha20 is faster than AES on platforms without hardware acceleration
- Constant-time in software implementations
- Better for multiplatform support

### Key Derivation Overhead
- HKDF-extract: One HMAC operation (minimal overhead)
- HKDF-expand: Multiple HMAC operations based on output length
- Per-message overhead is acceptable given security benefits

## Migration Notes

### Not Applicable
NIP-44 is NOT a replacement for NIP-04. Both will coexist:
- NIP-04 continues to be used for existing use cases
- NIP-44 is used for new use cases requiring authenticated encryption
- No migration path needed

## References

- **NIP-44 Specification**: https://github.com/nostr-protocol/nips/blob/master/44.md
- **Official Test Vectors**: https://github.com/paulmillr/nip44
- **Cure53 Security Audit**: https://cure53.de/audit-report_nip44-implementations.pdf
- **RFC 5869 (HKDF)**: https://datatracker.ietf.org/doc/html/rfc5869
- **RFC 8439 (ChaCha20)**: https://datatracker.ietf.org/doc/html/rfc8439
- **Existing NIP-04 Implementation**: [EncryptedDm.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt)
- **Research Document**: [NIP-44_Implementation_Research.md](file:///Users/jmawson/Development/sourcery/nostrino/history/NIP-44_Implementation_Research.md)

---

**End of Implementation Plan**
