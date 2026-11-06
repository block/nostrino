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

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/**
 * A channel in a [Buzz](https://github.com/block/buzz) community. [eventsUrl] is the community's HTTP endpoint for
 * submitting events, and [channelId] the channel's UUID, which is the channel's nip-29 group id.
 */
data class BuzzChannel(val eventsUrl: HttpUrl, val channelId: String) {

  companion object {
    /**
     * The channel [channelId] in the community at [relayUrl], the community's `https://` or `wss://` origin. Throws
     * [IllegalArgumentException] if [relayUrl] is neither, or [channelId] is not a lowercase UUID.
     */
    fun parse(relayUrl: String, channelId: String): BuzzChannel {
      val origin = relayUrl.trim().trimEnd('/')
      val eventsUrl = when {
        origin.startsWith("wss://") -> "https://" + origin.removePrefix("wss://")
        origin.startsWith("https://") -> origin
        else -> null
      }?.let { "$it/events".toHttpUrlOrNull() }
      requireNotNull(eventsUrl) { "Buzz relay URL must be an https:// or wss:// origin." }
      val id = channelId.trim()
      require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
        "Buzz channel id must be a lowercase UUID."
      }
      return BuzzChannel(eventsUrl, id)
    }
  }
}
