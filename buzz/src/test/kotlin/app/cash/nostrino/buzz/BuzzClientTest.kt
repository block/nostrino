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

import app.cash.nostrino.ArbPrimitive.arbUUID
import app.cash.nostrino.ArbPrimitive.arbVanillaString
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.HttpAuth
import com.squareup.moshi.Moshi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BuzzClientTest : StringSpec({

  "a post is a kind-9 message to the channel, which the relay stores" {
    val sec = arbSecKey.next()
    val text = arbVanillaString.next()
    val at = arbRecentSecond.next()
    val channel = createChannel(sec, "open")

    val posted = BuzzClient(sec).post(channel, text, at)

    posted.kind shouldBe 9
    posted.tags shouldBe listOf(listOf("h", channel.channelId))
    posted.content shouldBe text
    posted.pubKey shouldBe sec.pubKey.key
    posted.createdAt shouldBe at
    posted.id shouldBe nip01Id(posted)
    posted.validSignature shouldBe true
    messagesIn(channel, sec) shouldContainExactly listOf(posted)
  }

  "a retry re-sends the same message, which the relay stores once" {
    val sec = arbSecKey.next()
    val text = arbVanillaString.next()
    val at = arbRecentSecond.next()
    val channel = createChannel(sec, "open")
    val client = BuzzClient(sec)

    val first = client.post(channel, text, at)
    val retry = client.post(channel, text, at)

    retry shouldBe first
    messagesIn(channel, sec) shouldContainExactly listOf(first)
  }

  "a post containing line and paragraph separators is accepted as nip-01 hashes it" {
    val sec = arbSecKey.next()
    val text = "line\u2028separator paragraph\u2029separator"
    val channel = createChannel(sec, "open")

    val posted = BuzzClient(sec).post(channel, text, arbRecentSecond.next())

    posted.content shouldBe text
    messagesIn(channel, sec) shouldContainExactly listOf(posted)
  }

  "a post to a private channel by a non-member fails" {
    val channel = createChannel(arbSecKey.next(), "private")

    shouldThrow<IOException> { BuzzClient(arbSecKey.next()).post(channel, arbVanillaString.next()) }
  }

  "on a relay with closed membership, a non-member posts on a member's attestation" {
    val agent = arbSecKey.next()
    val channel = createChannel(relayOwner, "open", closedRelay)

    val posted = BuzzClient(agent, authTag = ownerAttestation(relayOwner, agent)).post(channel, arbVanillaString.next())

    posted.pubKey shouldBe agent.pubKey.key
    messagesIn(channel, relayOwner, closedRelay) shouldContainExactly listOf(posted)
  }

  "on a relay with closed membership, a non-member's post without an attestation fails" {
    val channel = createChannel(relayOwner, "open", closedRelay)

    shouldThrow<IOException> { BuzzClient(arbSecKey.next()).post(channel, arbVanillaString.next()) }
  }

  "a post to a channel that does not exist fails" {
    val channel = BuzzChannel(eventsUrl, arbUUID.next().toString())

    shouldThrow<IOException> { BuzzClient(arbSecKey.next()).post(channel, arbVanillaString.next()) }
  }

  "a post to an address with no relay fails" {
    val channel = BuzzChannel("http://localhost:1/events".toHttpUrl(), arbUUID.next().toString())

    shouldThrow<IOException> { BuzzClient(arbSecKey.next()).post(channel, arbVanillaString.next()) }
  }
}) {
  companion object {
    private val relay = "http://localhost:7708".toHttpUrl()
    private val eventsUrl = relay.resolve("/events")!!

    /** The fixture's relay that admits only its members, and NIP-OA attested keys, at which [relayOwner] is a member. */
    private val closedRelay = "http://localhost:7709".toHttpUrl()

    /** The closed relay's owner: the key whose secret is 1, from NIP-OA's test vectors. */
    private val relayOwner = SecKey(ByteString.of(*ByteArray(32).also { it[31] = 1 }))
    private val http = OkHttpClient()
    private val moshi = Moshi.Builder().build()
    private val canonical = moshi.adapter(List::class.java)

    /** A second in the last ten minutes, inside the relay's 15-minute window for an event's `created_at`. */
    val arbRecentSecond = arbitrary {
      Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(Arb.long(0L..600L).bind())
    }

    /** The event's id as nip-01 defines it: the hash of its serialised fields. */
    fun nip01Id(event: Event) = with(event) {
      canonical.toJson(listOf(0, pubKey.hex(), createdAt.epochSecond, kind, tags, content)).encodeUtf8().sha256()
    }

    /** A nip-oa `auth` tag, as JSON, by which [owner] authorizes [agent] with no conditions. */
    fun ownerAttestation(owner: SecKey, agent: SecKey): String {
      val sig = owner.sign("nostr:agent-auth:${agent.pubKey.hex()}:".encodeUtf8().sha256())
      return canonical.toJson(listOf("auth", owner.pubKey.hex(), "", sig.hex()))
    }

    /** A new channel with [visibility], created by [sec], who becomes its first member. */
    fun createChannel(sec: SecKey, visibility: String, at: HttpUrl = relay): BuzzChannel {
      val channelId = UUID.randomUUID().toString()
      val tags = listOf(
        listOf("h", channelId),
        listOf("name", "nostrino-test-$channelId"),
        listOf("visibility", visibility),
        listOf("channel_type", "stream"),
      )
      submit(sec, at.resolve("/events")!!, signed(sec, 9007, tags).toJson())
      return BuzzChannel(at.resolve("/events")!!, channelId)
    }

    /** The chat messages stored in [channel], as read by its member [sec]. */
    fun messagesIn(channel: BuzzChannel, sec: SecKey, at: HttpUrl = relay): List<Event> {
      val filter = listOf(mapOf("kinds" to listOf(9), "#h" to listOf(channel.channelId)))
      val events = moshi.adapter(List::class.java).fromJson(submit(sec, at.resolve("/query")!!, canonical.toJson(filter))).orEmpty()
      return events.map { Event.fromJson(moshi.adapter(Any::class.java).toJson(it)).shouldNotBeNull() }
    }

    private fun signed(sec: SecKey, kind: Int, tags: List<List<String>>): Event {
      val unsigned = Event(
        id = ByteString.EMPTY,
        pubKey = sec.pubKey.key,
        createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS),
        kind = kind,
        tags = tags,
        content = "",
        sig = ByteString.EMPTY,
      )
      val id = nip01Id(unsigned)
      return unsigned.copy(id = id, sig = sec.sign(id))
    }

    private fun submit(sec: SecKey, url: HttpUrl, body: String): String {
      val auth = HttpAuth(url.toString(), "POST", body.encodeUtf8().sha256(), UUID.randomUUID().toString()).sign(sec)
      val request = Request.Builder()
        .url(url)
        .header("Authorization", HttpAuth.header(auth))
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
      return http.newCall(request).execute().use { response ->
        val text = response.body.string()
        if (!response.isSuccessful) throw IOException("Buzz relay returned HTTP ${response.code}: $text")
        text
      }
    }
  }
}
