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

package app.cash.nostrino.model

import app.cash.nostrino.ArbPrimitive.arbByteString32
import app.cash.nostrino.ArbPrimitive.arbUUID
import app.cash.nostrino.ArbPrimitive.arbVanillaString
import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map

object ArbTags {

  val arbEventTag: Arb<EventTag> by lazy { arbByteString32.map { EventTag(it) } }
  val arbPubKeyTag: Arb<PubKeyTag> by lazy { arbPubKey.map { PubKeyTag(it) } }
  val arbHashTag: Arb<HashTag> by lazy { arbVanillaString.map { HashTag(it.replace(" ", "")) } }
  val arbRelaysTag: Arb<RelaysTag> by lazy { Arb.list(arbVanillaString, range = 1..10).map(::RelaysTag) }
  val arbAmountTag: Arb<AmountTag> by lazy { Arb.long(min = 1L).map { AmountTag(it) } }
  val arbLnUrlTag: Arb<LnUrlTag> by lazy { arbVanillaString.map(::LnUrlTag) }
  val arbGroupTag: Arb<GroupTag> by lazy { arbUUID.map { GroupTag(it.toString()) } }
  val arbUrlTag: Arb<UrlTag> by lazy { arbVanillaString.map { UrlTag("https://${it.replace(" ", "")}.example/events") } }
  val arbMethodTag: Arb<MethodTag> by lazy { Arb.element("GET", "POST", "PUT", "DELETE").map(::MethodTag) }
  val arbPayloadTag: Arb<PayloadTag> by lazy { arbByteString32.map(::PayloadTag) }
  val arbNonceTag: Arb<NonceTag> by lazy { arbUUID.map { NonceTag(it.toString()) } }
  val arbTag: Arb<Tag> by lazy {
    Arb.choice(
      arbEventTag,
      arbPubKeyTag,
      arbHashTag,
      arbRelaysTag,
      arbAmountTag,
      arbLnUrlTag,
      arbGroupTag,
      arbUrlTag,
      arbMethodTag,
      arbPayloadTag,
      arbNonceTag,
    )
  }

}
