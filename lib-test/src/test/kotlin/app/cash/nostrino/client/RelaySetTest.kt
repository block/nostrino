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

import app.cash.nostrino.crypto.PubKeyTest.Companion.arbPubKey
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.ArbEvent.arbEvent
import app.cash.nostrino.model.ArbEvent.arbSubscriptionId
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.Filter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

class RelaySetTest : StringSpec({
  fun relaySet(): RelaySet = RelaySet(
    setOf(FakeRelay(), FakeRelay(), FakeRelay())
  )

  "delegates start" {
    val set = relaySet()
    set.start()
    set.relays.map { it as FakeRelay }.map { it.started } shouldBe listOf(true, true, true)
  }

  "delegates stop" {
    val set = relaySet()
    set.stop()
    set.relays.map { it as FakeRelay }.map { it.stopped } shouldBe listOf(true, true, true)
  }

  "delegates send" {
    checkAll(Arb.list(arbEvent, 1..1)) { xs ->
      val set = relaySet()
      xs.forEach { set.send(it) }
      set.relays.map { it as FakeRelay }.map { it.sent } shouldBe listOf(xs, xs, xs)
    }
  }

  "delegates subscribe with single filter" {
    val set = relaySet()
    val subscription = set.subscribe(Filter.globalFeedNotes)
    set.relays.map { it as FakeRelay }.map { it.subscriptions } shouldBe listOf(
      mapOf(subscription to setOf(Filter.globalFeedNotes)),
      mapOf(subscription to setOf(Filter.globalFeedNotes)),
      mapOf(subscription to setOf(Filter.globalFeedNotes))
    )
  }

  "delegates subscribe with multiple filters" {
    val set = relaySet()
    val filters = setOf(Filter.globalFeedNotes, Filter.userMetaData(arbPubKey.next()))
    val subscription = set.subscribe(filters)
    set.relays.map { it as FakeRelay }.map { it.subscriptions } shouldBe listOf(
      mapOf(subscription to filters),
      mapOf(subscription to filters),
      mapOf(subscription to filters)
    )
  }

  "delegates unsubscribe" {
    val set = relaySet()
    val sub = set.subscribe(Filter.globalFeedNotes)
    set.unsubscribe(sub)
    set.relays.map { it as FakeRelay }.map { it.unsubscriptions } shouldBe listOf(setOf(sub), setOf(sub), setOf(sub))
  }

  "merge distinct events into a single flow" {
    val events = Arb.list(arbEvent, 20..20).next().distinctBy { it.id }.take(6)
    val set = RelaySet(
      setOf(
        FakeRelay(events.take(2).toMutableList()),
        FakeRelay(events.drop(2).take(2).toMutableList()),
        FakeRelay(events.drop(4).take(2).toMutableList())
      )
    )
    set.subscribe(Filter.globalFeedNotes)

    set.allEvents.toList() shouldContainExactly events
  }

  "merge duplicate events into a single flow" {
    val events = Arb.list(arbEvent, 20..20).next().distinctBy { it.id }.take(6).toMutableList()
    val set = RelaySet(
      setOf(
        FakeRelay(events),
        FakeRelay(events),
        FakeRelay(events)
      )
    )
    set.allEvents.toList() shouldContainExactly events
  }

  "merge partially overlapping events into a single flow" {
    val events = Arb.list(arbEvent, 20..20).next().distinctBy { it.id }.take(6).toMutableList()
    val set = RelaySet(
      setOf(
        FakeRelay(events.take(4).toMutableList()),
        FakeRelay(events.drop(1).take(4).toMutableList()),
        FakeRelay(events.drop(2).take(4).toMutableList())
      )
    )
    set.allEvents.toList() shouldContainExactly events
  }

  "merge events even with deduplication even when dupes are widely separated" {
    val events = Arb.list(arbEvent, 300..300).next().associateBy { it.id }.values
    val set = relaySet()
    events.forEach { set.send(it) }
    set.send(events.first())
    set.allEvents.toList() shouldContainExactly events
  }

  "merge relay messages into a single flow" {
    val events = Arb.list(arbEvent, 20..20).next().distinctBy { it.id }.take(6).toMutableList()
    val set = RelaySet(
      setOf(
        FakeRelay(events),
        FakeRelay(events),
        FakeRelay(events)
      )
    )

    set.relayMessages.toList().filterIsInstance<EventMessage>().map { it.event } shouldBeEqual events
  }
})

class FakeRelay(val sent: MutableList<Event> = mutableListOf()) : Relay() {

  var started = false
  override fun start() {
    started = true
  }

  var stopped = false
  override fun stop() {
    stopped = true
  }

  override fun send(event: Event) {
    sent.add(event)
  }

  val subscriptions = mutableMapOf<Subscription, Set<Filter>>()
  override fun subscribe(filters: Set<Filter>, subscription: Subscription): Subscription =
    subscription.also {
      subscriptions[subscription] = filters
    }

  val unsubscriptions = mutableSetOf<Subscription>()
  override fun unsubscribe(subscription: Subscription) {
    unsubscriptions.add(subscription)
  }

  override val relayMessages: Flow<RelayMessage>
    get() = sent.asSequence()
      .zip(arbSubscriptionId.samples())
      .map { (event, id) -> EventMessage(id.value, event) }
      .asFlow()

  override val allEvents: Flow<Event> = sent.asFlow()
}
