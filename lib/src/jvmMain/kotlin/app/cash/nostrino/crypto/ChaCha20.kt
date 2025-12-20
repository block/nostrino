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

import javax.crypto.Cipher
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object ChaCha20 {
  actual fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }

    val cipher = Cipher.getInstance("ChaCha20")
    val paramSpec = ChaCha20ParameterSpec(nonce, 0)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), paramSpec)
    return cipher.doFinal(plaintext)
  }

  actual fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(nonce.size == 12) { "Nonce must be 12 bytes" }

    val cipher = Cipher.getInstance("ChaCha20")
    val paramSpec = ChaCha20ParameterSpec(nonce, 0)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), paramSpec)
    return cipher.doFinal(ciphertext)
  }
}
