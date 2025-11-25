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

import app.cash.nostrino.model.Event
import app.cash.nostrino.model.Filter
import app.cash.nostrino.model.Tag
import app.cash.nostrino.model.TextNote
import app.cash.nostrino.model.UserMetaData
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import java.time.Instant

class ModelAdapter {

  @FromJson
  fun textNoteFromJson(text: String) = TextNote(text)

  @ToJson
  fun textNoteToJson(note: TextNote) = note.text

  @FromJson
  fun tagFromJson(strings: List<String>) = Tag.parseRaw(strings)

  @ToJson
  fun tagToJson(tag: Tag) = tag.toJsonList()

  @FromJson
  fun eventFromJson(reader: JsonReader): Event {
    var id: ByteString? = null
    var pubKey: ByteString? = null
    var createdAt: Instant? = null
    var kind: Int? = null
    var tags: List<List<String>>? = null
    var content: String? = null
    var sig: ByteString? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "id" -> id = reader.nextString().decodeHex()
        "pubkey" -> pubKey = reader.nextString().decodeHex()
        "created_at" -> createdAt = Instant.ofEpochSecond(reader.nextLong())
        "kind" -> kind = reader.nextInt()
        "tags" -> tags = readTagsList(reader)
        "content" -> content = reader.nextString()
        "sig" -> sig = reader.nextString().decodeHex()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return Event(
      id = id!!,
      pubKey = pubKey!!,
      createdAt = createdAt!!,
      kind = kind!!,
      tags = tags!!,
      content = content!!,
      sig = sig!!
    )
  }

  @ToJson
  fun eventToJson(writer: JsonWriter, event: Event) {
    writer.beginObject()
    writer.name("id").value(event.id.hex())
    writer.name("pubkey").value(event.pubKey.hex())
    writer.name("created_at").value(event.createdAt.epochSecond)
    writer.name("kind").value(event.kind)
    writer.name("tags")
    writeTagsList(writer, event.tags)
    writer.name("content").value(event.content)
    writer.name("sig").value(event.sig.hex())
    writer.endObject()
  }

  @FromJson
  fun filterFromJson(reader: JsonReader): Filter {
    var ids: Set<String>? = null
    var since: Instant? = null
    var authors: Set<String>? = null
    var kinds: Set<Int>? = null
    var eTags: Set<String>? = null
    var pTags: Set<String>? = null
    var tTags: Set<String>? = null
    var limit: Int? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "ids" -> ids = readStringSet(reader)
        "since" -> since = Instant.ofEpochSecond(reader.nextLong())
        "authors" -> authors = readStringSet(reader)
        "kinds" -> kinds = readIntSet(reader)
        "#e" -> eTags = readStringSet(reader)
        "#p" -> pTags = readStringSet(reader)
        "#t" -> tTags = readStringSet(reader)
        "limit" -> limit = reader.nextInt()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return Filter(
      ids = ids,
      since = since,
      authors = authors,
      kinds = kinds,
      eTags = eTags,
      pTags = pTags,
      tTags = tTags,
      limit = limit
    )
  }

  @ToJson
  fun filterToJson(writer: JsonWriter, filter: Filter) {
    writer.beginObject()
    filter.ids?.let { writer.name("ids"); writeStringSet(writer, it) }
    filter.since?.let { writer.name("since").value(it.epochSecond) }
    filter.authors?.let { writer.name("authors"); writeStringSet(writer, it) }
    filter.kinds?.let { writer.name("kinds"); writeIntSet(writer, it) }
    filter.eTags?.let { writer.name("#e"); writeStringSet(writer, it) }
    filter.pTags?.let { writer.name("#p"); writeStringSet(writer, it) }
    filter.tTags?.let { writer.name("#t"); writeStringSet(writer, it) }
    filter.limit?.let { writer.name("limit").value(it) }
    writer.endObject()
  }

  @FromJson
  fun userMetaDataFromJson(reader: JsonReader): UserMetaData {
    var name: String? = null
    var about: String? = null
    var picture: String? = null
    var nip05: String? = null
    var banner: String? = null
    var displayName: String? = null
    var website: String? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "name" -> name = reader.nextString()
        "about" -> about = reader.nextString()
        "picture" -> picture = reader.nextString()
        "nip05" -> nip05 = reader.nextString()
        "banner" -> banner = reader.nextString()
        "display_name" -> displayName = reader.nextString()
        "website" -> website = reader.nextString()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return UserMetaData(
      name = name,
      about = about,
      picture = picture,
      nip05 = nip05,
      banner = banner,
      displayName = displayName,
      website = website
    )
  }

  @ToJson
  fun userMetaDataToJson(writer: JsonWriter, metadata: UserMetaData) {
    writer.beginObject()
    metadata.name?.let { writer.name("name").value(it) }
    metadata.about?.let { writer.name("about").value(it) }
    metadata.picture?.let { writer.name("picture").value(it) }
    metadata.nip05?.let { writer.name("nip05").value(it) }
    metadata.banner?.let { writer.name("banner").value(it) }
    metadata.displayName?.let { writer.name("display_name").value(it) }
    metadata.website?.let { writer.name("website").value(it) }
    writer.endObject()
  }

  private fun readTagsList(reader: JsonReader): List<List<String>> {
    val tags = mutableListOf<List<String>>()
    reader.beginArray()
    while (reader.hasNext()) {
      val tag = mutableListOf<String>()
      reader.beginArray()
      while (reader.hasNext()) {
        tag.add(reader.nextString())
      }
      reader.endArray()
      tags.add(tag)
    }
    reader.endArray()
    return tags
  }

  private fun writeTagsList(writer: JsonWriter, tags: List<List<String>>) {
    writer.beginArray()
    tags.forEach { tag ->
      writer.beginArray()
      tag.forEach { writer.value(it) }
      writer.endArray()
    }
    writer.endArray()
  }

  private fun readStringSet(reader: JsonReader): Set<String> {
    val set = mutableSetOf<String>()
    reader.beginArray()
    while (reader.hasNext()) {
      set.add(reader.nextString())
    }
    reader.endArray()
    return set
  }

  private fun writeStringSet(writer: JsonWriter, set: Set<String>) {
    writer.beginArray()
    set.forEach { writer.value(it) }
    writer.endArray()
  }

  private fun readIntSet(reader: JsonReader): Set<Int> {
    val set = mutableSetOf<Int>()
    reader.beginArray()
    while (reader.hasNext()) {
      set.add(reader.nextInt())
    }
    reader.endArray()
    return set
  }

  private fun writeIntSet(writer: JsonWriter, set: Set<Int>) {
    writer.beginArray()
    set.forEach { writer.value(it) }
    writer.endArray()
  }
}
