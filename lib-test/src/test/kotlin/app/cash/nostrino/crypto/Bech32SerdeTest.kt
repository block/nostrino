/*
 * Copyright (c) 2026 Block, Inc.
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

import app.cash.nostrino.crypto.PubKeyTest.Companion.arbPubKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class Bech32SerdeTest : StringSpec({

  val invalidDataChars = (33..126).map { it.toChar() }
    .filterNot { it.lowercaseChar() in "qpzry9x8gf2tvdw0s3jn54khce6mua7l1" }

  "round trips any public key" {
    checkAll(arbPubKey) { pub ->
      val (hrp, data, encoding) = Bech32Serde.decodeBytes(pub.npub)
      hrp shouldBe "npub"
      data.toList() shouldBe pub.key.toByteArray().toList()
      encoding shouldBe Bech32Serde.Encoding.Bech32
    }
  }

  "rejects a character outside the bech32 alphabet" {
    checkAll(arbPubKey, Arb.int(5..62), Arb.element(invalidDataChars)) { pub, index, char ->
      val corrupted = pub.npub.replaceRange(index, index + 1, char.toString())
      shouldThrow<IllegalArgumentException> { Bech32Serde.decodeBytes(corrupted) }
    }
  }

  "rejects a character outside the bech32 alphabet without a checksum" {
    checkAll(arbPubKey, Arb.int(5..62), Arb.element(invalidDataChars)) { pub, index, char ->
      val corrupted = pub.npub.replaceRange(index, index + 1, char.toString())
      shouldThrow<IllegalArgumentException> { Bech32Serde.decodeBytes(corrupted, noChecksum = true) }
    }
  }

  "only ever fails with IllegalArgumentException" {
    checkAll(Arb.string(1..90)) { text ->
      runCatching { Bech32Serde.decodeBytes("npub1$text") }
        .exceptionOrNull()
        ?.shouldBeInstanceOf<IllegalArgumentException>()
    }
  }
})
