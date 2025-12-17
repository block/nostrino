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

  "encryption produces different output from input" {
    val plaintext = "Hello, World!".encodeToByteArray()
    val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }

    val encrypted = ChaCha20.encrypt(plaintext, key, nonce)

    encrypted.contentEquals(plaintext) shouldBe false
  }

  "different nonces produce different ciphertexts" {
    val plaintext = "Hello, World!".encodeToByteArray()
    val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val nonce1 = ByteArray(12).apply { SecureRandom().nextBytes(this) }
    val nonce2 = ByteArray(12).apply { SecureRandom().nextBytes(this) }

    val encrypted1 = ChaCha20.encrypt(plaintext, key, nonce1)
    val encrypted2 = ChaCha20.encrypt(plaintext, key, nonce2)

    encrypted1.contentEquals(encrypted2) shouldBe false
  }
})
