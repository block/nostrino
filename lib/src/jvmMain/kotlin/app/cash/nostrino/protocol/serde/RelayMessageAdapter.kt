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

package app.cash.nostrino.protocol.serde

import app.cash.nostrino.message.relay.CommandResult
import app.cash.nostrino.message.relay.EndOfStoredEvents
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.Notice
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.Event
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.ToJson
import okio.ByteString.Companion.decodeHex

class RelayMessageAdapter {

  @FromJson
  fun relayMessageFromJson(
    reader: JsonReader,
    eoseDelegate: JsonAdapter<EndOfStoredEvents>,
    commandDelegate: JsonAdapter<CommandResult>,
    noticeDelegate: JsonAdapter<Notice>,
    eventDelegate: JsonAdapter<EventMessage>
  ): RelayMessage {
    val peekyReader = reader.peekJson()
    peekyReader.beginArray()
    return when (val messageType = peekyReader.nextString()) {
      "EOSE" -> eoseDelegate.fromJson(reader)!!
      "OK" -> commandDelegate.fromJson(reader)!!
      "EVENT" -> eventDelegate.fromJson(reader)!!
      "NOTICE" -> noticeDelegate.fromJson(reader)!!
      else -> error("Unsupported message type: $messageType")
    }
  }

  @ToJson
  fun relayMessageToJson(message: RelayMessage) = when (message) {
    is CommandResult -> commandResultToJson(message)
    is EventMessage -> eventMessageToJson(message)
    is Notice -> noticeToJson(message)
    is EndOfStoredEvents -> eoseToJson(message)
    else -> error("Unsupported message type: ${message::class.qualifiedName}")
  }

  @FromJson
  fun eoseFromJson(reader: JsonReader): EndOfStoredEvents {
    reader.beginArray()
    require(reader.nextString() == "EOSE")
    val eose = EndOfStoredEvents(reader.nextString())
    reader.endArray()
    return eose
  }

  @ToJson
  fun eoseToJson(eose: EndOfStoredEvents) = listOf("EOSE", eose.subscriptionId)

  @FromJson
  fun commandResultFromJson(reader: JsonReader): CommandResult {
    reader.beginArray()
    require(reader.nextString() == "OK")
    val result = CommandResult(
      eventId = reader.nextString().decodeHex(),
      success = reader.nextBoolean(),
      message = if (reader.hasNext()) reader.nextString() else null
    )
    reader.endArray()
    return result
  }

  @ToJson
  fun commandResultToJson(cr: CommandResult) =
    listOfNotNull("OK", cr.eventId.hex(), cr.success, cr.message)

  @FromJson
  fun noticeFromJson(reader: JsonReader): Notice {
    reader.beginArray()
    require(reader.nextString() == "NOTICE")
    val notice = Notice(reader.nextString())
    reader.endArray()
    return notice
  }

  @ToJson
  fun noticeToJson(notice: Notice) = listOf("NOTICE", notice.message)

  @FromJson
  fun eventMessageFromJson(
    reader: JsonReader,
    delegate: JsonAdapter<Event>
  ): EventMessage {
    reader.beginArray()
    require(reader.nextString() == "EVENT")
    val subscriptionId = reader.nextString()
    val event = delegate.fromJson(reader)!!
    reader.endArray()
    return EventMessage(subscriptionId, event)
  }

  @ToJson
  fun eventMessageToJson(eventMessage: EventMessage) = listOf("EVENT", eventMessage.subscriptionId, eventMessage.event)
}
