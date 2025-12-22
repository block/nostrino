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

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

data class Nip44CipherText(
  val version: Byte,
  val nonce: ByteString,
  val ciphertext: ByteString,
  val mac: ByteString
) {
  init {
    require(version == VERSION_2) { "Only version 2 is supported" }
    require(nonce.size == 32) { "Nonce must be 32 bytes" }
    require(mac.size == 32) { "MAC must be 32 bytes" }
  }

  fun toBase64(): String {
    val payload = ByteArray(1 + 32 + ciphertext.size + 32)
    payload[0] = version
    nonce.toByteArray().copyInto(payload, destinationOffset = 1)
    ciphertext.toByteArray().copyInto(payload, destinationOffset = 33)
    mac.toByteArray().copyInto(payload, destinationOffset = 33 + ciphertext.size)
    return payload.toByteString().base64()
  }

  override fun toString(): String = toBase64()

  companion object {
    const val VERSION_2: Byte = 0x02

    fun parse(payload: String): Nip44CipherText {
      require(payload.isNotEmpty() && payload[0] != '#') { "Unknown version" }
      require(payload.length in 132..87472) { "Invalid payload size" }

      val decoded = payload.decodeBase64()
        ?: throw IllegalArgumentException("Invalid base64 encoding")

      require(decoded.size in 99..65603) { "Invalid data size" }

      val version = decoded[0]
      require(version == VERSION_2) { "Unknown version $version" }

      val nonce = decoded.substring(1, 33)
      val mac = decoded.substring(decoded.size - 32)
      val ciphertext = decoded.substring(33, decoded.size - 32)

      return Nip44CipherText(version, nonce, ciphertext, mac)
    }
  }
}
