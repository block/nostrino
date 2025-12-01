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

actual object SchnorrSigner {
  actual fun sign(payload: ByteArray, privateKey: ByteArray): ByteArray =
    Secp256k1.signSchnorr(payload, privateKey, null)

  actual fun verify(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean =
    Secp256k1.verifySchnorr(signature, data, publicKey)
}
