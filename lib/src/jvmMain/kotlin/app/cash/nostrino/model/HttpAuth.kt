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

package app.cash.nostrino.model

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Authorization for one HTTP request to [url] with [method]. Event kind 27235, as defined in
 * [nip-98](https://github.com/nostr-protocol/nips/blob/master/98.md). [payload] is the SHA-256 hash of the request
 * body. [nonce] makes otherwise identical authorizations distinct, for servers that refuse a replayed one.
 */
data class HttpAuth(
  val url: String,
  val method: String,
  val payload: ByteString? = null,
  val nonce: String? = null,
  override val tags: List<Tag> = listOfNotNull(
    UrlTag(url),
    MethodTag(method),
    payload?.let(::PayloadTag),
    nonce?.let(::NonceTag),
  ),
) : EventContent {

  override val kind: Int = Companion.kind

  override fun toJsonString(): String = ""

  companion object {
    const val kind = 27235

    /** The `Authorization` header value that presents [event], a signed [HttpAuth], to the server. */
    fun header(event: Event): String = "Nostr " + event.toJson().encodeUtf8().base64()
  }
}
