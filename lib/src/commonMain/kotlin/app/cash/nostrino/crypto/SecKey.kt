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

  /** the public key derived from this secret key */
  val pubKey by lazy {
    PubKey(Secp256k1Operations.pubKeyCompress(Secp256k1Operations.pubkeyCreate(key.toByteArray())).copyOfRange(1, 33).toByteString())
  }

  /** sign any arbitrary payload with this key */
  fun sign(payload: ByteString): ByteString =
    Secp256k1Operations.signSchnorr(payload.toByteArray(), key.toByteArray(), null).toByteString()

  /** Find the point of shared secret between this sec key and a pub key */
  fun sharedSecretWith(pub: PubKey): ByteArray =
    Secp256k1Operations.pubKeyTweakMul(
      pubKey = byteArrayOf(0x02) + pub.key.toByteArray(),
      tweak = key.toByteArray()
    ).copyOfRange(1, 33)

  /** Generate cipher text of the provided plain text, intended for the provided pub key */


  companion object {
    /** Create secret key from nip-19 bech32 encoded string */
    fun parse(bech32: String): SecKey {
      val (hrp, key) = Bech32Serde.decodeBytes(bech32, false)
      require(hrp == "nsec") { "Unsupported encoding hrp=$hrp" }
      return SecKey(key.toByteString())
    }
  }
}
