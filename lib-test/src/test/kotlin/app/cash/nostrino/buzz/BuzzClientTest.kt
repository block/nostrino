/*
 * Copyright (c) 2026 Block, Inc.
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

package app.cash.nostrino.buzz

import app.cash.nostrino.ArbPrimitive.arbInstantSeconds
import app.cash.nostrino.ArbPrimitive.arbUUID
import app.cash.nostrino.ArbPrimitive.arbVanillaString
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.Event
import com.squareup.moshi.Moshi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.arbitrary.next
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException

class BuzzClientTest : StringSpec({

  "a post is a kind-9 message to the channel, authorized for that exact request" {
    val sec = arbSecKey.next()
    val text = arbVanillaString.next()
    val at = arbInstantSeconds.next()
    withRelay(acknowledging) { relay ->
      val channel = BuzzChannel(relay.url("/events"), arbUUID.next().toString())

      val posted = BuzzClient(sec).post(channel, text, at)

      val request = relay.takeRequest()
      request.method shouldBe "POST"
      request.path shouldBe "/events"
      val body = request.body.readUtf8()
      val message = Event.fromJson(body).shouldNotBeNull()
      message shouldBe posted
      message.kind shouldBe 9
      message.tags shouldBe listOf(listOf("h", channel.channelId))
      message.content shouldBe text
      message.pubKey shouldBe sec.pubKey.key
      message.createdAt shouldBe at
      message.id shouldBe nip01Id(message)
      message.validSignature shouldBe true

      val authorization = request.getHeader("Authorization").shouldNotBeNull()
      authorization shouldStartWith "Nostr "
      val auth = Event.fromJson(authorization.removePrefix("Nostr ").decodeBase64().shouldNotBeNull().utf8())
        .shouldNotBeNull()
      auth.kind shouldBe 27235
      auth.tags.filterNot { it.first() == "nonce" } shouldBe listOf(
        listOf("u", channel.eventsUrl.toString()),
        listOf("method", "POST"),
        listOf("payload", body.encodeUtf8().sha256().hex()),
      )
      auth.content shouldBe ""
      auth.pubKey shouldBe sec.pubKey.key
      auth.id shouldBe nip01Id(auth)
      auth.validSignature shouldBe true
    }
  }

  "a retry re-sends the same message under a fresh authorization" {
    val sec = arbSecKey.next()
    val text = arbVanillaString.next()
    val at = arbInstantSeconds.next()
    withRelay(acknowledging) { relay ->
      val channel = BuzzChannel(relay.url("/events"), arbUUID.next().toString())
      val client = BuzzClient(sec)

      client.post(channel, text, at).id shouldBe client.post(channel, text, at).id

      val first = relay.takeRequest()
      val second = relay.takeRequest()
      first.body.readUtf8() shouldBe second.body.readUtf8()
      first.getHeader("Authorization") shouldNotBe second.getHeader("Authorization")
    }
  }

  "a post the relay does not acknowledge fails" {
    val sec = arbSecKey.next()
    listOf(
      MockResponse().setResponseCode(500),
      MockResponse().setBody("""{"accepted":false,"event_id":"00","message":"blocked: not a member"}"""),
      MockResponse().setBody("""{"accepted":true,"event_id":"${"0".repeat(64)}","message":""}"""),
    ).forEach { response ->
      withRelay(respondingWith(response)) { relay ->
        val channel = BuzzChannel(relay.url("/events"), arbUUID.next().toString())
        shouldThrow<IOException> { BuzzClient(sec).post(channel, arbVanillaString.next()) }
      }
    }
  }
}) {
  companion object {
    private val canonical = Moshi.Builder().build().adapter(List::class.java)

    /** The event's id as nip-01 defines it: the hash of its serialised fields. */
    fun nip01Id(event: Event) = with(event) {
      canonical.toJson(listOf(0, pubKey.hex(), createdAt.epochSecond, kind, tags, content)).encodeUtf8().sha256()
    }

    fun withRelay(dispatcher: Dispatcher, test: (MockWebServer) -> Unit) =
      MockWebServer().use { relay ->
        relay.dispatcher = dispatcher
        relay.start()
        test(relay)
      }

    /** Accepts every event, acknowledging it by its own id as a Buzz relay does. */
    val acknowledging = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val id = Event.fromJson(request.body.clone().readUtf8())!!.id.hex()
        return MockResponse().setBody("""{"accepted":true,"event_id":"$id","message":""}""")
      }
    }

    fun respondingWith(response: MockResponse) = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = response
    }
  }
}
