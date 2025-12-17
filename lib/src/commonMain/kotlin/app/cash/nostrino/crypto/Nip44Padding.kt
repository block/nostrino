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

import kotlin.math.floor
import kotlin.math.log2

object Nip44Padding {
  private const val MIN_PLAINTEXT_SIZE = 1
  private const val MAX_PLAINTEXT_SIZE = 65535

  fun calcPaddedLen(unpadded: Int): Int {
    require(unpadded in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
      "Plaintext length must be between $MIN_PLAINTEXT_SIZE and $MAX_PLAINTEXT_SIZE bytes"
    }

    if (unpadded <= 32) return 32

    val nextPower = 1 shl (floor(log2((unpadded - 1).toDouble())).toInt() + 1)
    val chunk = if (nextPower <= 256) 32 else nextPower / 8

    return chunk * ((unpadded - 1) / chunk + 1)
  }

  fun pad(plaintext: String): ByteArray {
    val unpadded = plaintext.encodeToByteArray()
    val unpaddedLen = unpadded.size

    require(unpaddedLen in MIN_PLAINTEXT_SIZE..MAX_PLAINTEXT_SIZE) {
      "Plaintext length must be between $MIN_PLAINTEXT_SIZE and $MAX_PLAINTEXT_SIZE bytes"
    }

    val paddedLen = calcPaddedLen(unpaddedLen)
    val result = ByteArray(2 + paddedLen)

    result[0] = (unpaddedLen shr 8).toByte()
    result[1] = (unpaddedLen and 0xFF).toByte()

    unpadded.copyInto(result, destinationOffset = 2)

    return result
  }

  fun unpad(padded: ByteArray): String {
    require(padded.size >= 2) { "Padded data must be at least 2 bytes" }

    val unpaddedLen = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)

    require(unpaddedLen > 0) { "Invalid padding: length is zero" }
    require(padded.size >= 2 + unpaddedLen) { "Invalid padding: data too short" }
    require(padded.size == 2 + calcPaddedLen(unpaddedLen)) { "Invalid padding: incorrect padded length" }

    for (i in 2 + unpaddedLen until padded.size) {
      require(padded[i] == 0.toByte()) { "Invalid padding: non-zero padding bytes" }
    }

    return padded.decodeToString(startIndex = 2, endIndex = 2 + unpaddedLen)
  }
}
