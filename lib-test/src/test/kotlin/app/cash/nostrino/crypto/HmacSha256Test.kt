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
import java.security.SecureRandom

class HmacSha256Test : StringSpec({

  "compute generates 32 byte output" {
    val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val message = "Hello, World!".encodeToByteArray()

    val result = HmacSha256.compute(key, message)

    result.size shouldBe 32
  }

  "computeWithAad generates 32 byte output" {
    val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val aad = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val message = "Hello, World!".encodeToByteArray()

    val result = HmacSha256.computeWithAad(key, aad, message)

    result.size shouldBe 32
  }

  "isEqualConstantTime returns true for equal arrays" {
    val a = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val b = a.copyOf()

    HmacSha256.isEqualConstantTime(a, b) shouldBe true
  }

  "isEqualConstantTime returns false for different arrays" {
    val a = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val b = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    HmacSha256.isEqualConstantTime(a, b) shouldBe false
  }

  "isEqualConstantTime returns false for arrays with different lengths" {
    val a = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    val b = ByteArray(16).apply { SecureRandom().nextBytes(this) }

    HmacSha256.isEqualConstantTime(a, b) shouldBe false
  }
})
