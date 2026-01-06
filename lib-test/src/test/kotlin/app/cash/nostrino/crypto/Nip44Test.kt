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
import io.kotest.property.checkAll
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class Nip44Test : StringSpec({

  "getConversationKey matches test vector" {
    val sec1 = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()
    val sec2 = "0000000000000000000000000000000000000000000000000000000000000002".decodeHex()
    val expected = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d"

    val secKey1 = SecKey(sec1)
    val secKey2 = SecKey(sec2)
    val sharedSecret = secKey1.sharedSecretWith(secKey2.pubKey)
    val conversationKey = Nip44.getConversationKey(sharedSecret)

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
    checkAll(arbSecKey, arbSecKey, Arb.string(1..1000)) { secKey1, secKey2, plaintext ->
      val sharedSecret = secKey1.sharedSecretWith(secKey2.pubKey)
      val conversationKey = Nip44.getConversationKey(sharedSecret)

      val encrypted = Nip44.encrypt(plaintext, conversationKey)
      val decrypted = Nip44.decrypt(encrypted, conversationKey)

      decrypted shouldBe plaintext
    }
  }
})
