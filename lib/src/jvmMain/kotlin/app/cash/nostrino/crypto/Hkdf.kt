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
