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
 *
 */

package app.cash.nostrino.crypto

import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Nostr secret key.
 */
data class SecKey(val key: ByteString) : Key {
  override fun toString() = "SecKey(███)"

  /** nip-19 bech32 encoded form of this key */
  val nsec by lazy {
    Bech32Serde.encodeBytes("nsec", key, Bech32Serde.Encoding.Bech32)
  }

  override fun encoded(): String = nsec

  override fun hex(): String = key.hex()

  val pubKey by lazy {
    PubKey(EcdhProvider.derivePublicKey(key.toByteArray()).toByteString())
  }

  fun sign(payload: ByteString): ByteString =
    SchnorrSigner.sign(payload.toByteArray(), key.toByteArray()).toByteString()

  fun sharedSecretWith(pub: PubKey): ByteArray =
    EcdhProvider.sharedSecret(key.toByteArray(), pub.key.toByteArray())


  companion object {
    /** Create secret key from nip-19 bech32 encoded string */
    fun parse(bech32: String): SecKey {
      val (hrp, key) = Bech32Serde.decodeBytes(bech32, false)
      require(hrp == "nsec") { "Unsupported encoding hrp=$hrp" }
      return SecKey(key.toByteString())
    }
  }
}
