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
 * HMAC-based Extract-and-Expand Key Derivation Function (HKDF).
 *
 * HKDF is a key derivation function specified in RFC 5869, used to derive
 * cryptographic keys from a shared secret. Used in NIP-44 to derive
 * encryption keys from ECDH shared secrets.
 *
 * @see <a href="https://tools.ietf.org/html/rfc5869">RFC 5869</a>
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/44.md">NIP-44</a>
 */
expect object Hkdf {
  /**
   * HKDF-Extract: derives a pseudorandom key from input key material.
   *
   * @param salt Optional salt value (a non-secret random value)
   * @param ikm Input key material (e.g., shared secret from ECDH)
   * @return Pseudorandom key (PRK)
   */
  fun extract(salt: ByteArray, ikm: ByteArray): ByteArray

  /**
   * HKDF-Expand: expands a pseudorandom key to the desired length.
   *
   * @param prk Pseudorandom key from extract step
   * @param info Optional context and application-specific information
   * @param length Desired output length in bytes
   * @return Derived key material of specified length
   */
  fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray
}
