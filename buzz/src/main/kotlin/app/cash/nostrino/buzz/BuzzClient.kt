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

import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.GroupChatMessage
import app.cash.nostrino.model.HttpAuth
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import com.squareup.moshi.JsonAdapter
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Posts chat messages to [Buzz](https://github.com/block/buzz) channels as [sec]. Each message is a kind-9
 * [GroupChatMessage] tagged with the channel, POSTed to the community's events endpoint with [HttpAuth]
 * authentication. [sec] must belong to a member of the channel. [authTag], a
 * [NIP-OA](https://github.com/block/buzz/blob/main/docs/nips/NIP-OA.md) `auth` tag as JSON, lets [sec] post to a
 * relay with closed membership on behalf of the member who signed it. The relay verifies its signature; the client
 * checks only its shape, and throws [IllegalArgumentException] if it is not an `auth` tag by a key other than [sec] or
 * not a valid HTTP header value.
 */
class BuzzClient(
  private val sec: SecKey,
  private val httpClient: OkHttpClient = OkHttpClient(),
  private val authTag: String? = null,
) {

  init {
    authTag?.let { tag ->
      require(runCatching { headersOf("x-auth-tag", tag) }.isSuccess) {
        "Buzz auth tag must be a valid HTTP header value."
      }
      require(isAttestation(tag)) {
        "Buzz auth tag must be a NIP-OA tag [\"auth\",owner,conditions,signature] by a key other than the poster's."
      }
    }
  }

  /**
   * Post [text] to [channel] as created at [at], and return the posted event once the relay has acknowledged it.
   * Throws [IOException] if the request fails or the relay does not acknowledge the event. A refusal's message carries
   * the relay's reason.
   *
   * The event's id derives only from [text], [channel] and [at] (to the second), so a retry with the same values
   * re-sends an event with the same id, which the relay stores once, rather than posting a second message. Buzz rejects an
   * event whose [at] is more than 15 minutes from the relay's clock.
   */
  fun post(channel: BuzzChannel, text: String, at: Instant = Instant.now()): Event {
    val message = GroupChatMessage(text, channel.channelId).sign(sec, at)
    val body = message.toJson()
    val auth = HttpAuth(
      url = channel.eventsUrl.toString(),
      method = "POST",
      payload = body.encodeUtf8().sha256(),
      nonce = UUID.randomUUID().toString(),
    ).sign(sec)
    val request = Request.Builder()
      .url(channel.eventsUrl)
      .header("Authorization", HttpAuth.header(auth))
      .apply { authTag?.let { header("x-auth-tag", it) } }
      .post(body.toRequestBody("application/json".toMediaType()))
      .build()
    val acknowledgement = httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("Buzz relay returned HTTP ${response.code}: ${response.peekBody(MAX_REASON_BYTES).string()}")
      }
      runCatching { acknowledgementAdapter.fromJson(response.body.source()) }
        .getOrElse { throw IOException("Buzz relay returned a malformed acknowledgement.", it) }
    }
    if (acknowledgement?.get("accepted") != true || acknowledgement["event_id"] != message.id.hex()) {
      throw IOException("Buzz relay did not acknowledge event ${message.id.hex()}: $acknowledgement")
    }
    return message
  }

  private fun isAttestation(tag: String): Boolean {
    val elements = runCatching { tagAdapter.fromJson(tag) }.getOrNull().orEmpty()
    val strings = elements.filterIsInstance<String>()
    return strings.size == 4 &&
      elements.size == 4 &&
      strings[0] == "auth" &&
      strings[1].isLowercaseHex(64) &&
      strings[1] != sec.pubKey.hex() &&
      strings[3].isLowercaseHex(128)
  }

  private fun String.isLowercaseHex(length: Int): Boolean =
    this.length == length && all { it in '0'..'9' || it in 'a'..'f' }

  private companion object {
    val MAX_REASON_BYTES = 1024L
    val acknowledgementAdapter: JsonAdapter<Map<*, *>> = moshi.adapter(Map::class.java)
    val tagAdapter: JsonAdapter<List<*>> = moshi.adapter(List::class.java)
  }
}
