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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import okhttp3.HttpUrl.Companion.toHttpUrl

class BuzzChannelTest : StringSpec({

  "a wss or https origin posts to the community's events endpoint" {
    checkAll(arbUUID) { uuid ->
      val channelId = uuid.toString()
      BuzzChannel.parse(" wss://buzz.example.com/ ", channelId) shouldBe
        BuzzChannel("https://buzz.example.com/events".toHttpUrl(), channelId)
      BuzzChannel.parse("https://buzz.example.com", " $channelId ") shouldBe
        BuzzChannel("https://buzz.example.com/events".toHttpUrl(), channelId)
    }
  }

  "an insecure origin or a channel that is not a lowercase UUID is refused" {
    checkAll(arbUUID) { uuid ->
      val channelId = uuid.toString()
      shouldThrow<IllegalArgumentException> { BuzzChannel.parse("http://buzz.example.com", channelId) }
      shouldThrow<IllegalArgumentException> { BuzzChannel.parse("ws://buzz.example.com", channelId) }
      shouldThrow<IllegalArgumentException> { BuzzChannel.parse("buzz.example.com", channelId) }
      shouldThrow<IllegalArgumentException> { BuzzChannel.parse("wss://buzz.example.com", channelId.uppercase()) }
      shouldThrow<IllegalArgumentException> { BuzzChannel.parse("wss://buzz.example.com", "general") }
    }
  }
})
