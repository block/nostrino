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

import app.cash.nostrino.ArbPrimitive
import app.cash.nostrino.ArbPrimitive.arbByteString32
import app.cash.nostrino.ArbPrimitive.arbUUID
import app.cash.nostrino.message.relay.CommandResult
import app.cash.nostrino.message.relay.EndOfStoredEvents
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.Notice
import app.cash.nostrino.protocol.serde.NostrJson
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string

object ArbEvent {
  val moshi = NostrJson.moshi

  val arbEventId by lazy { arbByteString32.map { it.hex() } }

  val arbEventContent: Arb<EventContent> by lazy {
    Arb.choice(
      ArbEventContent.arbTextNote,
      ArbEventContent.arbEncryptedDm,
      ArbEventContent.arbEventDeletion,
      ArbEventContent.arbUserMetaData,
      ArbEventContent.arbReaction,
      ArbEventContent.arbZapRequest
    )
  }

  val arbEventWithContent: Arb<Pair<Event, EventContent>> by lazy {
    Arb.bind(
      arbByteString32,
      arbByteString32,
      ArbPrimitive.arbInstantSeconds,
      arbEventContent,
      ArbPrimitive.arbByteString64
    ) { id, pubKey, createdAt, content, sig ->
      val event = Event(
        id = id,
        pubKey = pubKey,
        createdAt = createdAt,
        kind = content.kind,
        tags = content.tags.map { it.toJsonList() },
        content = content.toJsonString(),
        sig = sig
      )
      event to content
    }
  }
  val arbEvent: Arb<Event> by lazy { arbEventWithContent.map { it.first } }

  val arbSubscriptionId = arbUUID.map { it.toString() }
  val arbEndOfStoredEvents = arbSubscriptionId.map { EndOfStoredEvents(it) }
  val arbNotice = Arb.string().map { Notice(it) }
  val arbCommandResult = Arb.bind(
    arbByteString32,
    Arb.boolean(),
    Arb.string().orNull()
  ) { id, success, message ->
    CommandResult(id, success, message)
  }

  val arbEventMessage: Arb<EventMessage> =
    Arb.bind(arbSubscriptionId, arbEvent) { subscriptionId, event ->
      EventMessage(subscriptionId, event)
    }

  val arbRelayMessage = Arb.choice(
    arbEndOfStoredEvents,
    arbCommandResult,
    arbEventMessage,
    arbNotice
  )
}
