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

package app.cash.nostrino.model

import app.cash.nostrino.message.relay.CommandResult
import app.cash.nostrino.message.relay.EndOfStoredEvents
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.Notice
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.ArbEvent.arbCommandResult
import app.cash.nostrino.model.ArbEvent.arbEndOfStoredEvents
import app.cash.nostrino.model.ArbEvent.arbEvent
import app.cash.nostrino.model.ArbEvent.arbEventMessage
import app.cash.nostrino.model.ArbEvent.arbNotice
import app.cash.nostrino.model.ArbEvent.arbRelayMessage
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.checkAll

class RelayMessageSerializationTest : StringSpec({

  "can serde end_of_stored_events" {
    val adapter = moshi.adapter(EndOfStoredEvents::class.java)
    checkAll(arbEndOfStoredEvents) {
      val json = adapter.toJson(it)
      json shouldStartWith """["EOSE","""
      adapter.fromJson(json) shouldBe it
    }
  }

  "can serde command_result" {
    val adapter = moshi.adapter(CommandResult::class.java)
    checkAll(arbCommandResult) {
      val json = adapter.toJson(it)
      json shouldStartWith """["OK","""
      adapter.fromJson(json) shouldBe it
    }
  }

  "can serde notice" {
    val adapter = moshi.adapter(Notice::class.java)
    checkAll(arbNotice) {
      val json = adapter.toJson(it)
      json shouldStartWith """["NOTICE","""
      adapter.fromJson(json) shouldBe it
    }
  }

  "can serde events" {
    val adapter = moshi.adapter(Event::class.java)
    checkAll(arbEvent) {
      val json = adapter.toJson(it)
      adapter.fromJson(json) shouldBe it
    }
  }

  "can serde event messages" {
    val adapter = moshi.adapter(EventMessage::class.java)
    checkAll(arbEventMessage) {
      val json = adapter.toJson(it)
      json shouldStartWith """["EVENT","${it.subscriptionId}","""
      adapter.fromJson(json) shouldBe it
    }
  }

  "can serde any relay message" {
    val adapter = moshi.adapter(RelayMessage::class.java)
    checkAll(arbRelayMessage) {
      adapter.fromJson(adapter.toJson(it)) shouldBe it
    }
  }
})
