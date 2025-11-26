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

import fr.acinq.secp256k1.Secp256k1

actual object Secp256k1Operations {
  actual fun pubkeyCreate(secKey: ByteArray): ByteArray =
    Secp256k1.pubkeyCreate(secKey)

  actual fun pubKeyCompress(pubKey: ByteArray): ByteArray =
    Secp256k1.pubKeyCompress(pubKey)

  actual fun signSchnorr(data: ByteArray, secKey: ByteArray, auxRand: ByteArray?): ByteArray =
    Secp256k1.signSchnorr(data, secKey, auxRand)

  actual fun verifySchnorr(signature: ByteArray, data: ByteArray, pubKey: ByteArray): Boolean =
    Secp256k1.verifySchnorr(signature, data, pubKey)

  actual fun pubKeyTweakMul(pubKey: ByteArray, tweak: ByteArray): ByteArray =
    Secp256k1.pubKeyTweakMul(pubKey, tweak)
}
