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
 * HMAC-SHA256 message authentication code.
 *
 * Hash-based Message Authentication Code using SHA-256 as the hash function.
 * Used in NIP-44 for message authentication and key derivation (HKDF).
 *
 * @see <a href="https://tools.ietf.org/html/rfc2104">RFC 2104</a>
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/44.md">NIP-44</a>
 */
expect object HmacSha256 {
  /**
   * Computes HMAC-SHA256 of a message.
   *
   * @param key The secret key
   * @param message The message to authenticate
   * @return The 32-byte HMAC
   */
  fun compute(key: ByteArray, message: ByteArray): ByteArray

  /**
   * Computes HMAC-SHA256 with additional authenticated data (AAD).
   *
   * @param key The secret key
   * @param aad Additional authenticated data prepended to message
   * @param message The message to authenticate
   * @return The 32-byte HMAC
   */
  fun computeWithAad(key: ByteArray, aad: ByteArray, message: ByteArray): ByteArray

  /**
   * Compares two byte arrays in constant time to prevent timing attacks.
   *
   * @param a First byte array
   * @param b Second byte array
   * @return True if arrays are equal, false otherwise
   */
  fun isEqualConstantTime(a: ByteArray, b: ByteArray): Boolean
}
