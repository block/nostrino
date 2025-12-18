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

/**
 * ChaCha20 stream cipher implementation.
 *
 * ChaCha20 is a high-speed stream cipher designed by Daniel J. Bernstein.
 * Used in NIP-44 for encrypting/decrypting Nostr messages.
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/44.md">NIP-44</a>
 */
expect object ChaCha20 {
  /**
   * Encrypts plaintext using ChaCha20.
   *
   * @param plaintext The data to encrypt
   * @param key The 32-byte encryption key
   * @param nonce The 12-byte nonce
   * @return The encrypted ciphertext
   */
  fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray

  /**
   * Decrypts ciphertext using ChaCha20.
   *
   * @param ciphertext The encrypted data
   * @param key The 32-byte decryption key
   * @param nonce The 12-byte nonce
   * @return The decrypted plaintext
   */
  fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
}
