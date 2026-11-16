/*
 * Copyright (c) 2023 Block, Inc.
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import okio.ByteString.Companion.toByteString

class SecKeyTest : StringSpec({
  "never leaks the secret key in toString" {
    checkAll(arbSecKey) { sec ->
      sec.toString() shouldBe "SecKey(███)"
    }
  }

  "can be used to generate a public key" {
    checkAll(arbSecKey) { sec ->
      sec.pubKey.npub shouldMatch "npub1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{58}"
    }
  }

  "can always be converted to nip-19 bech32 encoded secret key" {
    checkAll(arbSecKey) { sec ->
      sec.nsec shouldMatch "nsec1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{58}"
    }
  }

  "can always be restored from nip-19 bech32 encoded secret key" {
    checkAll(arbSecKey) { sec ->
      SecKey.parse(sec.nsec) shouldBe sec
    }
  }

  "will refuse to convert any other kind of bech32 encoded string" {
    checkAll(Arb.stringPattern("[A-Za-z0-9]{4,12}")) { text ->
      val encoding = text.take(3).lowercase()
      val encoded = Bech32Serde.encodeBytes(encoding, text.toByteArray().toByteString(), Bech32Serde.Encoding.Bech32)
      shouldThrow<IllegalArgumentException> { SecKey.parse(encoded) }
        .message shouldBe "Unsupported encoding hrp=$encoding"
    }
  }

  "signs with fresh auxiliary randomness, so repeated signatures differ and each verifies" {
    checkAll(arbSecKey, Arb.byteArray(Arb.constant(32), Arb.byte())) { sec, payload ->
      val first = sec.sign(payload.toByteString())
      val second = sec.sign(payload.toByteString())
      first shouldNotBe second
      listOf(first, second).forEach { signature ->
        SchnorrSigner.verify(signature.toByteArray(), payload, sec.pubKey.key.toByteArray()) shouldBe true
      }
    }
  }

  "hex form should be the hex form of the key (its a shortcut)" {
    checkAll(arbSecKey) { sec ->
      sec.hex() shouldBe sec.key.hex()
    }
  }
})
