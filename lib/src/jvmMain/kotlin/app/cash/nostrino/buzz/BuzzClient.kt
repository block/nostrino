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
import com.squareup.moshi.JsonDataException
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
 * authentication. [sec] must belong to a member of the channel.
 */
class BuzzClient(
  private val sec: SecKey,
  private val httpClient: OkHttpClient = OkHttpClient(),
) {

  /**
   * Post [text] to [channel] as created at [at], and return the posted event once the relay has acknowledged it.
   * Throws [IOException] if the request fails or the relay does not acknowledge the event.
   *
   * The event's id derives only from [text], [channel] and [at] (to the second), so a retry with the same values
   * re-sends the same event, which the relay stores once, rather than posting a second message.
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
      .post(body.toRequestBody("application/json".toMediaType()))
      .build()
    val acknowledgement = httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("Buzz relay returned HTTP ${response.code}.")
      try {
        acknowledgementAdapter.fromJson(response.body.source())
      } catch (e: JsonDataException) {
        throw IOException("Buzz relay returned a malformed acknowledgement.", e)
      }
    }
    if (acknowledgement?.get("accepted") != true || acknowledgement["event_id"] != message.id.hex()) {
      throw IOException("Buzz relay did not acknowledge event ${message.id.hex()}: $acknowledgement")
    }
    return message
  }

  private companion object {
    val acknowledgementAdapter: JsonAdapter<Map<*, *>> = moshi.adapter(Map::class.java)
  }
}
