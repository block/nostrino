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
import org.kotlincrypto.SecureRandom

class SecKeyGenerator {

  /** Generate a new secret key */
  tailrec fun generate(): SecKey {
    val bs = ByteArray(32)
    SecureRandom().nextBytesCopyTo(bs)
    val sec = bs.toByteString()
    return if (sec.hex() < CURVE_ORDER && sec != ZERO) SecKey(sec) else generate()
  }

  companion object {
    private const val CURVE_ORDER =
      "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"
    private val ZERO = ByteString.of(*ByteArray(32))
  }
}
