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

import com.squareup.moshi.Json
import app.cash.nostrino.protocol.serde.NostrJson.moshi

/**
 * User metadata (profile). Event kind 0, as defined in
 * [nip-01](https://github.com/nostr-protocol/nips/blob/master/01.md#basic-event-kinds).
 */
data class UserMetaData(
  val name: String? = null,
  val about: String? = null,
  val picture: String? = null,
  val nip05: String? = null,
  val banner: String? = null,
  @Json(name = "display_name")
  val displayName: String? = null,
  val website: String? = null,
  override val tags: List<Tag> = emptyList(),
) : EventContent {

  override val kind: Int = UserMetaData.kind

  override fun toJsonString(): String = adapter.toJson(this)

  companion object {
    const val kind: Int = 0
    private val adapter by lazy { moshi.adapter(UserMetaData::class.java) }
  }
}
