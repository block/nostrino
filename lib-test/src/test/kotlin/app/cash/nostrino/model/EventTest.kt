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

import app.cash.nostrino.crypto.SecKeyGenerator
import app.cash.nostrino.model.ArbEvent.arbEventContent
import app.cash.nostrino.model.ArbEvent.arbEventWithContent
import app.cash.nostrino.model.ArbEventContent.arbTextNote
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant

class EventTest : StringSpec({

  "event id hashes content exactly as nip-01 serializes it" {
    val sec = SecKeyGenerator().generate()
    val content = "line\u2028para\u2029tab\tbell\u0007 esc\u001b quote\" slash\\ é 🚀"
    val event = TextNote(content).sign(sec, Instant.ofEpochSecond(1_700_000_000))
    val escaped = "line\u2028para\u2029tab\\tbell\\u0007 esc\\u001b quote\\\" slash\\\\ é 🚀"
    val canonical = """[0,"${sec.pubKey.key.hex()}",1700000000,1,[],"$escaped"]"""

    event.id shouldBe canonical.encodeUtf8().sha256()
  }

  "can rehydrated event content" {
    checkAll(arbEventWithContent) { (event, content) ->
      event.content() shouldBe content
    }
  }

  "signed event has valid signature" {
    checkAll(arbTextNote) {note ->
      val sec = SecKeyGenerator().generate()
      val event = note.sign(sec)
      event.validSignature shouldBe true
    }
  }

  "wrong public key does not have valid signature" {
    val sec = SecKeyGenerator().generate()
    val (pubKey) = SecKeyGenerator().generate().pubKey

    val note = arbTextNote.next()
    val event = note.sign(sec).copy(pubKey = pubKey)

    event.validSignature shouldBe false
  }

  "parsed event has valid signature" {
    val rawEvent = """
      {
        "content": "Sorry it took us so long to get here, but we thought our sweet npub would be worth the wait.",
        "created_at": 1684358892,
        "id": "32157d937c68c3f2d66809ac475577f27aee05c3b3bc1f061c70d24b7481005f",
        "kind": 1,
        "pubkey": "c7617e84337c611c7d5f941b35b1ec51f2ae6e9f41aac9616092d510e1c295e0",
        "sig": "e68306cecbb9934521d1039c849970f968b3d9db67b0790bf66ac089e6039079e876ffb428c09bcb609a7b0379d3cf2a69bc26494c6003ee9d3d40a7926b5eb8",
        "tags": []
      }
    """.trimIndent()
    val event = Event.fromJson(rawEvent)

    event?.validSignature shouldBe true
  }

  "toJson returns valid event json" {
    checkAll(arbEventContent) {content ->
      val sec = SecKeyGenerator().generate()
      val event = content.sign(sec)

      val json = event.toJson()

      Event.fromJson(json) shouldBe event
    }
  }
})
