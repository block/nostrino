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

package app.cash.nostrino.protocol.serde

/**
 * The serialization that event ids hash, as defined in
 * [nip-01](https://github.com/nostr-protocol/nips/blob/master/01.md). Relays recompute ids from these exact bytes,
 * which Moshi cannot produce because it always escapes U+2028 and U+2029.
 */
internal object CanonicalJson {

  fun eventPreimage(
    pubKeyHex: String,
    createdAt: Long,
    kind: Int,
    tags: List<List<String>>,
    content: String,
  ): String = buildString {
    append("[0,").appendString(pubKeyHex).append(',').append(createdAt).append(',').append(kind).append(",[")
    tags.forEachIndexed { i, tag ->
      if (i > 0) append(',')
      append('[')
      tag.forEachIndexed { j, value ->
        if (j > 0) append(',')
        appendString(value)
      }
      append(']')
    }
    append("],").appendString(content).append(']')
  }

  private fun StringBuilder.appendString(value: String): StringBuilder = apply {
    append('"')
    value.forEach { c ->
      when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        '\b' -> append("\\b")
        '\u000c' -> append("\\f")
        in '\u0000'..'\u001f' -> append("\\u%04x".format(c.code))
        else -> append(c)
      }
    }
    append('"')
  }
}
