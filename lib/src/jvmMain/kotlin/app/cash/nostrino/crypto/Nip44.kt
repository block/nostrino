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

/**
 * NIP-44 encrypted direct messages implementation.
 *
 * Provides versioned, authenticated encryption for Nostr direct messages using
 * ChaCha20-Poly1305-like construction with HKDF key derivation and HMAC-SHA256
 * authentication.
 *
 * ## Usage
 * ```kotlin
 * // Derive conversation key from ECDH shared secret
 * val sharedSecret = secKey1.sharedSecretWith(pubKey2)
 * val conversationKey = Nip44.getConversationKey(sharedSecret)
 *
 * // Encrypt a message
 * val encrypted = Nip44.encrypt("Hello, Nostr!", conversationKey)
 * val base64Payload = encrypted.toBase64()
 *
 * // Decrypt a message
 * val received = Nip44CipherText.parse(base64Payload)
 * val plaintext = Nip44.decrypt(received, conversationKey)
 * ```
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/44.md">NIP-44</a>
 */
object Nip44 {
  private const val SALT = "nip44-v2"

  /**
   * Derives a conversation key from an ECDH shared secret.
   *
   * Uses HKDF-Extract with salt "nip44-v2" to derive a conversation key that
   * can be used for multiple messages between the same two parties.
   *
   * @param sharedSecret The 32-byte ECDH shared secret
   * @return The 32-byte conversation key
   * @throws IllegalArgumentException if shared secret is not 32 bytes
   */
  fun getConversationKey(sharedSecret: ByteArray): ByteArray {
    require(sharedSecret.size == 32) { "Shared secret must be 32 bytes" }
    return Hkdf.extract(SALT.encodeToByteArray(), sharedSecret)
  }

  /**
   * Derives per-message encryption keys from a conversation key and nonce.
   *
   * Uses HKDF-Expand to derive ChaCha20 key, ChaCha20 nonce, and HMAC key
   * from the conversation key and message nonce. These keys are used for
   * a single message encryption/decryption.
   *
   * @param conversationKey The 32-byte conversation key
   * @param nonce The 32-byte random nonce (unique per message)
   * @return MessageKeys containing ChaCha20 key (32 bytes), ChaCha20 nonce (12 bytes), and HMAC key (32 bytes)
   * @throws IllegalArgumentException if conversationKey or nonce are not 32 bytes
   */
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

  /**
   * Generates a cryptographically secure random nonce.
   *
   * @return A 32-byte random nonce suitable for message encryption
   */
  fun generateNonce(): ByteArray = ByteArray(32).apply {
    SecureRandom().nextBytes(this)
  }

  /**
   * Encrypts a plaintext message using NIP-44.
   *
   * Encrypts the message with the following steps:
   * 1. Pads the plaintext using NIP-44 padding scheme
   * 2. Derives message keys from conversation key and nonce
   * 3. Encrypts padded plaintext with ChaCha20
   * 4. Computes HMAC-SHA256 over nonce and ciphertext
   * 5. Securely clears derived keys from memory
   *
   * @param plaintext The message to encrypt (1-65535 bytes UTF-8)
   * @param conversationKey The 32-byte conversation key
   * @param nonce The 32-byte random nonce (auto-generated if not provided)
   * @return Nip44CipherText containing version, nonce, ciphertext, and MAC
   * @throws IllegalArgumentException if plaintext length is invalid or keys are wrong size
   */
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

  /**
   * Decrypts a NIP-44 encrypted message.
   *
   * Decrypts the message with the following steps:
   * 1. Derives message keys from conversation key and payload nonce
   * 2. Verifies HMAC-SHA256 using constant-time comparison
   * 3. Decrypts ciphertext with ChaCha20
   * 4. Removes NIP-44 padding
   * 5. Securely clears derived keys from memory
   *
   * @param payload The encrypted message payload
   * @param conversationKey The 32-byte conversation key
   * @return The decrypted plaintext message
   * @throws IllegalArgumentException if MAC verification fails, padding is invalid, or keys are wrong size
   */
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

  /**
   * Container for per-message cryptographic keys.
   *
   * Holds the ephemeral keys derived for encrypting/decrypting a single message.
   * Keys should be cleared from memory after use via [clear].
   */
  data class MessageKeys(
    /** The 32-byte ChaCha20 encryption key */
    val chachaKey: ByteArray,
    /** The 12-byte ChaCha20 nonce */
    val chachaNonce: ByteArray,
    /** The 32-byte HMAC-SHA256 key */
    val hmacKey: ByteArray
  ) {
    /**
     * Securely clears all keys from memory by overwriting with zeros.
     */
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
