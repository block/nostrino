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

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.lang.NullPointerException

/** Encrypted text. Contains the ciphertext bytes and an initialisation vector to be used during the AES decryption. */
data class CipherText(
  val cipherText: ByteString,
  val iv: ByteString
) {
  override fun toString() = "${cipherText.base64()}?iv=${iv.base64()}"

  fun decipher(from: PubKey, to: SecKey): String {
    val sharedSecret = to.sharedSecretWith(from)
    return String(AesCipher.decrypt(cipherText.toByteArray(), sharedSecret, iv.toByteArray()))
  }

  companion object {

    /** Parse a string value of ciphertext + initialisation vector into a CipherText instance. */
    fun parse(value: String): CipherText {
      val parts = value.split("?iv=")
      require(parts.size == 2) { "Invalid cipherText (should be \"\${cipher}?iv=\${iv}\"): $value" }
      return try {
        CipherText(parts[0].decodeBase64()!!, parts[1].decodeBase64()!!)
      } catch (e: NullPointerException) {
        throw IllegalArgumentException("Invalid cipherText (bad base64): $value")
      }
    }
  }
}
