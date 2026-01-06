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

import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

object Nip44 {
  private const val SALT = "nip44-v2"

  fun getConversationKey(sharedSecret: ByteArray): ByteArray {
    require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }
    return Hkdf.extract(SALT.encodeToByteArray(), sharedSecret)
  }

  fun getMessageKeys(
    conversationKey: ByteArray,
    nonce: ByteArray
  ): MessageKeys {
    require(conversationKey.size == 32) { "Conversation key must be 32 bytes" }
    require(nonce.size == 32) { "Nonce must be 32 bytes" }

    val expanded = Hkdf.expand(conversationKey, nonce, 76)

    return MessageKeys(
      chachaKey = expanded.copyOfRange(0, 32),
      chachaNonce = expanded.copyOfRange(32, 44),
      hmacKey = expanded.copyOfRange(44, 76)
    )
  }

  fun generateNonce(): ByteArray = ByteArray(32).apply {
    SecureRandom().nextBytes(this)
  }

  fun encrypt(
    plaintext: String,
    conversationKey: ByteArray,
    nonce: ByteArray = generateNonce()
  ): Nip44CipherText {
    val keys = getMessageKeys(conversationKey, nonce)

    val padded = Nip44Padding.pad(plaintext)
    val ciphertext = ChaCha20.encrypt(padded, keys.chachaKey, keys.chachaNonce)
    val mac = HmacSha256.computeWithAad(keys.hmacKey, nonce, ciphertext)

    keys.clear()

    return Nip44CipherText(
      version = Nip44CipherText.VERSION_2,
      nonce = nonce.toByteString(),
      ciphertext = ciphertext.toByteString(),
      mac = mac.toByteString()
    )
  }

  fun decrypt(
    payload: Nip44CipherText,
    conversationKey: ByteArray
  ): String {
    val keys = getMessageKeys(conversationKey, payload.nonce.toByteArray())

    val calculatedMac = HmacSha256.computeWithAad(
      keys.hmacKey,
      payload.nonce.toByteArray(),
      payload.ciphertext.toByteArray()
    )

    require(HmacSha256.isEqualConstantTime(calculatedMac, payload.mac.toByteArray())) {
      "Invalid MAC"
    }

    val paddedPlaintext = ChaCha20.decrypt(
      payload.ciphertext.toByteArray(),
      keys.chachaKey,
      keys.chachaNonce
    )

    keys.clear()

    return Nip44Padding.unpad(paddedPlaintext)
  }

  data class MessageKeys(
    val chachaKey: ByteArray,
    val chachaNonce: ByteArray,
    val hmacKey: ByteArray
  ) {
    fun clear() {
      chachaKey.fill(0)
      chachaNonce.fill(0)
      hmacKey.fill(0)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is MessageKeys) return false
      if (!chachaKey.contentEquals(other.chachaKey)) return false
      if (!chachaNonce.contentEquals(other.chachaNonce)) return false
      if (!hmacKey.contentEquals(other.hmacKey)) return false
      return true
    }

    override fun hashCode(): Int {
      var result = chachaKey.contentHashCode()
      result = 31 * result + chachaNonce.contentHashCode()
      result = 31 * result + hmacKey.contentHashCode()
      return result
    }
  }
}
