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
import app.cash.nostrino.ArbPrimitive.arbEmoji
import app.cash.nostrino.ArbPrimitive.arbInstantSeconds
import app.cash.nostrino.ArbPrimitive.arbVanillaString
import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.ArbEvent.arbEvent
import app.cash.nostrino.model.ArbEvent.arbEventId
import app.cash.nostrino.model.ArbTags.arbAmountTag
import app.cash.nostrino.model.ArbTags.arbEventTag
import app.cash.nostrino.model.ArbTags.arbGroupTag
import app.cash.nostrino.model.ArbTags.arbMethodTag
import app.cash.nostrino.model.ArbTags.arbNonceTag
import app.cash.nostrino.model.ArbTags.arbPayloadTag
import app.cash.nostrino.model.ArbTags.arbUrlTag
import app.cash.nostrino.model.ArbTags.arbLnUrlTag
import app.cash.nostrino.model.ArbTags.arbPubKeyTag
import app.cash.nostrino.model.ArbTags.arbRelaysTag
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.triple

object ArbEventContent {

  val arbEncryptedDm: Arb<EncryptedDm> by lazy {
    Arb.triple(arbSecKey, arbSecKey, Arb.string()).map { (from, to, message) ->
      EncryptedDm(from, to.pubKey, message)
    }
  }

  val arbEventDeletion: Arb<EventDeletion> by lazy {
    Arb.bind(
      arbVanillaString,
      Arb.set(arbByteString32, 1..8)
    ) { note, eventIds -> EventDeletion(note, eventIds) }
  }

  val arbReaction: Arb<Reaction> by lazy {
    Arb.triple(arbByteString32, arbPubKey, arbEmoji)
      .flatMap { (e, p, c) ->
        Arb.choose(
          3 to Upvote(e, p),
          3 to Downvote(e, p),
          1 to EmojiReact(String(Character.toChars(c)), e, p)
        )
      }
  }

  val arbTextNote: Arb<TextNote> by lazy {
    Arb.pair(
      Arb.string(minSize = 1),
      Arb.list(ArbTags.arbTag, 0..5)
    ).map { (content, tags) -> TextNote(content, tags) }
  }

  val arbGroupChatMessage: Arb<GroupChatMessage> by lazy {
    Arb.pair(Arb.string(), arbGroupTag).map { (text, group) -> GroupChatMessage(text, group.groupId) }
  }

  val arbHttpAuth: Arb<HttpAuth> by lazy {
    Arb.bind(
      arbUrlTag,
      arbMethodTag,
      arbPayloadTag.orNull(),
      arbNonceTag.orNull(),
    ) { url, method, payload, nonce -> HttpAuth(url.url, method.method, payload?.sha256, nonce?.nonce) }
  }

  val arbUserMetaData: Arb<UserMetaData> by lazy {
    Arb.bind(
      arbVanillaString.orNull(),
      arbVanillaString.orNull(),
      arbVanillaString.orNull(),
      arbVanillaString.orNull(),
      arbVanillaString.orNull(),
      arbVanillaString.orNull(),
      arbVanillaString.orNull()
    ) { name, about, picture, nip05, banner, displayName, website ->
      UserMetaData(name, about, picture, nip05, banner, displayName, website)
    }
  }

  val arbZapRequest: Arb<ZapRequest> by lazy {
    Arb.bind(
      arbVanillaString,
      arbRelaysTag,
      arbAmountTag,
      arbLnUrlTag,
      arbPubKeyTag,
      arbEventTag
    ) { content, relays, amount, lnurl, pubKey, event ->
      ZapRequest(content, relays.relays, amount.amount, lnurl.lnurl, pubKey.pubKey, event.eventId)
    }
  }

  val arbZapReceipt: Arb<ZapReceipt> by lazy {
    Arb.bind(
      arbPubKeyTag,
      arbEventTag,
      arbVanillaString,
      arbEvent,
      arbByteString32
    ) { pubkey, eventTag, bolt11, event, preimage ->
      ZapReceipt(pubkey.pubKey, eventTag.eventId, bolt11, event.copy(kind = ZapRequest.kind), preimage)
    }
  }

  val arbFilter: Arb<Filter> by lazy {
    Arb.bind(
      Arb.set(arbEventId).orNull(),
      arbInstantSeconds.orNull(),
      Arb.set(arbPubKey.map { it.key.hex() }).orNull(),
    ) { ids, since, authors ->
      Filter(
        ids = ids,
        since = since,
        authors = authors,
      )
    }
  }
}
