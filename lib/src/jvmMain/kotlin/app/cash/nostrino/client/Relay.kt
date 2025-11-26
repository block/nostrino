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
 */

package app.cash.nostrino.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.EncryptedDm
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.Filter
import app.cash.nostrino.model.Reaction
import app.cash.nostrino.model.TextNote
import app.cash.nostrino.model.UserMetaData

abstract class Relay {

  /** Begin sending and receiving events with this Relay */
  abstract fun start()

  /** Stop sending and receiving events with this Relay */
  abstract fun stop()

  /** Queue an event to be sent (potentially immediately) */
  abstract fun send(event: Event)

  /** Create a new subscription with exactly one filter */
  fun subscribe(
    filter: Filter,
    subscription: Subscription = Subscription(randomSubscriptionId())
  ) = subscribe(setOf(filter), subscription)

  /** Create a new subscription with zero to many filters */
  abstract fun subscribe(
    filters: Set<Filter>,
    subscription: Subscription = Subscription(randomSubscriptionId())
  ): Subscription

  /** Unsubscribe from a subscription */
  abstract fun unsubscribe(subscription: Subscription)

  /** All messages transmitted by this relay for our active subscriptions */
  abstract val relayMessages : Flow<RelayMessage>

  /** The subset of [RelayMessage] that only contain messages of type [EventMessage] */
  abstract val allEvents: Flow<Event>

  /** The subset of [allEvents] that are of type [TextNote] */
  val notes: Flow<Event> by lazy { allEvents.filter { it.kind == TextNote.kind } }

  /** The subset of [allEvents] that are of type [EncryptedDm] */
  val directMessages: Flow<Event> by lazy { allEvents.filter { it.kind == EncryptedDm.kind } }

  /** The subset of [allEvents] that are of type [UserMetaData] */
  val userMetaData: Flow<Event> by lazy { allEvents.filter { it.kind == UserMetaData.kind } }

  /** The subset of [allEvents] that are of type [Reaction] */
  val reactions: Flow<Event> by lazy { allEvents.filter { it.kind == Reaction.kind } }
}
