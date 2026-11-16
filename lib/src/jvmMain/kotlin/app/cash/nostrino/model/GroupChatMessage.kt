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

/**
 * A chat message in a relay-based group. Event kind 9, as defined in
 * [nip-29](https://github.com/nostr-protocol/nips/blob/master/29.md).
 */
data class GroupChatMessage(
  val text: String,
  val groupId: String,
  override val tags: List<Tag> = listOf(GroupTag(groupId)),
) : EventContent {

  override val kind: Int = Companion.kind

  override fun toJsonString(): String = text

  companion object {
    const val kind = 9
  }
}
