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

import app.cash.nostrino.ArbPrimitive.arbByteString32
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import okio.ByteString.Companion.toByteString

class Nip44CipherTextTest : StringSpec({

  "parse and encode round-trip" {
    checkAll(arbNip44CipherText) { cipherText ->
      val encoded = cipherText.toBase64()
      val parsed = Nip44CipherText.parse(encoded)

      parsed shouldBe cipherText
    }
  }

  "parse validates version" {
    checkAll(arbNip44CipherText) { ct ->
      ct.version shouldBe 0x02.toByte()
    }
  }
}) {
  companion object {
    private val arbCiphertext = Arb.list(Arb.byte(), 34..1000)
      .map { it.toByteArray().toByteString() }

    private val arbNip44CipherText = Arb.bind(
      arbByteString32,
      arbCiphertext,
      arbByteString32
    ) { nonce, ciphertext, mac ->
      Nip44CipherText(
        version = Nip44CipherText.VERSION_2,
        nonce = nonce,
        ciphertext = ciphertext,
        mac = mac
      )
    }
  }
}
