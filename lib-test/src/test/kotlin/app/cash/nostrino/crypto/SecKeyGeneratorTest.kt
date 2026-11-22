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
 *
 */

package app.cash.nostrino.crypto

import app.cash.nostrino.crypto.ArbKeys.arbHash
import fr.acinq.secp256k1.Secp256k1
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class SecKeyGeneratorTest : StringSpec({

  "generates valid keys" {
    checkAll(arbHash) { hash ->
      val sec = SecKeyGenerator().generate()
      val sig = sec.sign(hash)
      Secp256k1.get().verifySchnorr(sig.toByteArray(), hash.toByteArray(), sec.pubKey.key.toByteArray()) shouldBe true
    }
  }

  "generates keys across the whole key space" {
    val leadingNibbles = List(1_000) { SecKeyGenerator().generate().hex().first() }.toSet()
    leadingNibbles shouldContainAll "0123456789abcdef".toList()
  }
})
