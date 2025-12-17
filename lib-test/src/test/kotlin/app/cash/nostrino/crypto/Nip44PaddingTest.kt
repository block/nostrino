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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class Nip44PaddingTest : StringSpec({

  "calcPaddedLen returns 32 for messages up to 32 bytes" {
    Nip44Padding.calcPaddedLen(1) shouldBe 32
    Nip44Padding.calcPaddedLen(16) shouldBe 32
    Nip44Padding.calcPaddedLen(32) shouldBe 32
  }

  "calcPaddedLen returns correct values for known inputs" {
    Nip44Padding.calcPaddedLen(33) shouldBe 64
    Nip44Padding.calcPaddedLen(64) shouldBe 64
    Nip44Padding.calcPaddedLen(65) shouldBe 96
    Nip44Padding.calcPaddedLen(100) shouldBe 128
    Nip44Padding.calcPaddedLen(200) shouldBe 224
    Nip44Padding.calcPaddedLen(1000) shouldBe 1024
    Nip44Padding.calcPaddedLen(10000) shouldBe 10240
    Nip44Padding.calcPaddedLen(65535) shouldBe 65536
  }

  "calcPaddedLen throws for invalid lengths" {
    shouldThrow<IllegalArgumentException> {
      Nip44Padding.calcPaddedLen(0)
    }
    shouldThrow<IllegalArgumentException> {
      Nip44Padding.calcPaddedLen(65536)
    }
  }

  "pad and unpad round-trip" {
    checkAll(Arb.string(1..1000)) { plaintext ->
      val padded = Nip44Padding.pad(plaintext)
      val unpadded = Nip44Padding.unpad(padded)

      unpadded shouldBe plaintext
    }
  }

  "pad includes length prefix" {
    val plaintext = "Hello"
    val padded = Nip44Padding.pad(plaintext)

    val length = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
    length shouldBe plaintext.length
  }

  "pad includes null padding bytes" {
    val plaintext = "Hi"
    val padded = Nip44Padding.pad(plaintext)

    val unpaddedLen = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
    for (i in 2 + unpaddedLen until padded.size) {
      padded[i] shouldBe 0.toByte()
    }
  }

  "unpad validates padding" {
    val validPadded = Nip44Padding.pad("Hello")
    val invalidPadded = validPadded.copyOf()
    invalidPadded[invalidPadded.size - 1] = 42.toByte()

    shouldThrow<IllegalArgumentException> {
      Nip44Padding.unpad(invalidPadded)
    }
  }
})
