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
 *
 */

package app.cash.nostrino.model

import app.cash.nostrino.model.ArbTags.arbAmountTag
import app.cash.nostrino.model.ArbTags.arbEventTag
import app.cash.nostrino.model.ArbTags.arbHashTag
import app.cash.nostrino.model.ArbTags.arbLnUrlTag
import app.cash.nostrino.model.ArbTags.arbPubKeyTag
import app.cash.nostrino.model.ArbTags.arbRelaysTag
import app.cash.nostrino.model.ArbTags.arbTag
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class TagTest : StringSpec({
  "event tag json list should be e :: event_id hex" {
    checkAll(arbEventTag) { tag ->
      tag.toJsonList() shouldBe listOf("e", tag.eventId.hex())
    }
  }

  "pubkey tag json list should be p :: pubkey hex" {
    checkAll(arbPubKeyTag) { tag ->
      tag.toJsonList() shouldBe listOf("p", tag.pubKey.hex())
    }
  }

  "hashtag json list should be t :: label" {
    checkAll(arbHashTag) { tag ->
      tag.toJsonList() shouldBe listOf("t", tag.label)
    }
  }

  "relays tag json list should be relays :: values" {
    checkAll(arbRelaysTag) { tag ->
      tag.toJsonList() shouldBe listOf("relays") + tag.relays
    }
  }

  "amount tag json list should be amount :: value" {
    checkAll(arbAmountTag) { tag ->
      tag.toJsonList() shouldBe listOf("amount", tag.amount.toString())
    }
  }

  "lnurltag json list should be t :: label" {
    checkAll(arbLnUrlTag) { tag ->
      tag.toJsonList() shouldBe listOf("lnurl", tag.lnurl)
    }
  }

  "any tag can be ser/de" {
    checkAll(arbTag) { tag ->
      Tag.parseRaw(tag.toJsonList()) shouldBe tag
    }
  }

  "empty string list fails to parse as a tag" {
    shouldThrow<IllegalArgumentException> { Tag.parseRaw(emptyList()) }
  }

  "tag without value should fail to parse" {
    shouldThrow<IllegalArgumentException> { Tag.parseRaw(listOf("e")) }
    shouldThrow<IllegalArgumentException> { Tag.parseRaw(listOf("p")) }
    shouldThrow<IllegalArgumentException> { Tag.parseRaw(listOf("t")) }
  }

  "string list with incorrect tag should fail to parse" {
    val knownTags = setOf("e", "p", "t", "d", "amount", "lnurl", "relays", "bolt11", "preimage", "description", "encryption", "notifications")
    checkAll(
      arbTag,
      Arb.string(1..20).filterNot { knownTags.contains(it) }
    ) { tag, c ->
      shouldThrow<IllegalArgumentException> { Tag.parseRaw(listOf(c).plus(tag.toJsonList().drop(1))) }
    }
  }

})
