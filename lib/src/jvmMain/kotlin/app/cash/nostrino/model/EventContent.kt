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

import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import com.squareup.moshi.JsonAdapter
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant
import java.time.temporal.ChronoUnit

/** A type that can be signed and converted into an Event */
interface EventContent {

  val kind: Int

  val tags: List<Tag>

  fun toJsonString(): String

  fun sign(sec: SecKey): Event {
    val createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
    val contentJson = toJsonString()
    val tagsJson = tags.map { it.toJsonList() }
    val elements = listOf(0, sec.pubKey.key.hex(), createdAt.epochSecond, kind, tagsJson, contentJson)
    val toJson = jsonListAdapter.toJson(elements)
    val id = toJson.encodeUtf8().sha256()
    val sig = sec.sign(id)
    return Event(id, sec.pubKey.key, createdAt, kind, tagsJson, contentJson, sig)
  }


  fun asZapRequest(): ZapRequest? =
    if (this is ZapRequest) this
    else null

  companion object {
    val jsonListAdapter: JsonAdapter<List<*>> = moshi.adapter(List::class.java)
  }
}
