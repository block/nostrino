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

  "extract with test vector from RFC 5869" {
    val salt = "nip44-v2".encodeToByteArray()
    val sharedSecret = "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268".decodeHex()

    val result = Hkdf.extract(salt, sharedSecret.toByteArray())

    result.size shouldBe 32
  }

  "expand generates correct length output" {
    val prk = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".decodeHex()
    val info = "0000000000000000000000000000000000000000000000000000000000000001".decodeHex()

    val result = Hkdf.expand(prk.toByteArray(), info.toByteArray(), 76)

    result.size shouldBe 76
  }
})
