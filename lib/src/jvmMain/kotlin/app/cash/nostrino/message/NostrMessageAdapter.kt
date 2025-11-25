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

package app.cash.nostrino.message

import app.cash.nostrino.message.relay.AuthChallenge
import app.cash.nostrino.message.relay.CommandResult
import app.cash.nostrino.message.relay.EndOfStoredEvents
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.Notice
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.Tag
import app.cash.nostrino.model.TextNote
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import java.time.Instant

/** Moshi adapters for JSON types in the protocol. */
class NostrMessageAdapter {

  // === RelayMessages

  @FromJson
  fun relayMessageFromJson(
    reader: JsonReader,
    eoseDelegate: JsonAdapter<EndOfStoredEvents>,
    commandDelegate: JsonAdapter<CommandResult>,
    noticeDelegate: JsonAdapter<Notice>,
    eventDelegate: JsonAdapter<EventMessage>,
    authDelegate: JsonAdapter<AuthChallenge>
  ): RelayMessage {
    val peekyReader = reader.peekJson()
    peekyReader.beginArray()
    return when (val messageType = peekyReader.nextString()) {
      "EOSE" -> eoseDelegate.fromJson(reader)!!
      "OK" -> commandDelegate.fromJson(reader)!!
      "EVENT" -> eventDelegate.fromJson(reader)!!
      "NOTICE" -> noticeDelegate.fromJson(reader)!!
      "AUTH" -> authDelegate.fromJson(reader)!!
      else -> error("Unsupported message type: $messageType")
    }
  }

  @ToJson
  fun relayMessageToJson(message: RelayMessage) = when (message) {
    is CommandResult -> commandResultToJson(message)
    is EventMessage -> eventMessageToJson(message)
    is Notice -> noticeToJson(message)
    is EndOfStoredEvents -> eoseToJson(message)
    is AuthChallenge -> authChallengeToJson(message)
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

  @FromJson
  fun authChallengeFromJson(reader: JsonReader): AuthChallenge = reader.run {
    beginArray()
    require(nextString() == "AUTH")
    AuthChallenge(nextString()).also { endArray() }
  }

  @ToJson
  fun authChallengeToJson(authChallenge: AuthChallenge) = listOf("AUTH", authChallenge.challenge)

  @FromJson
  fun textNoteFromJson(text: String) = TextNote(text)

  @ToJson
  fun textNoteToJson(note: TextNote) = note.text

  @FromJson
  fun tagFromJson(strings: List<String>) = Tag.parseRaw(strings)

  @ToJson
  fun tagToJson(tag: Tag) = tag.toJsonList()

  // === primitives

  @FromJson
  fun byteStringFromJson(s: String): ByteString = s.decodeHex()

  @ToJson
  fun byteStringToJson(b: ByteString): String = b.hex()

  @FromJson
  fun instantFromJson(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

  @ToJson
  fun instantToJson(i: Instant): Long = i.epochSecond

  companion object {
    val moshi = Moshi.Builder()
      .add(NostrMessageAdapter())
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }
}
