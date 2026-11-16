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

package app.cash.nostrino.client

import app.cash.nostrino.crypto.SecKeyGenerator
import app.cash.nostrino.model.ArbEventContent.arbReaction
import app.cash.nostrino.model.ArbEventContent.arbTextNote
import app.cash.nostrino.model.ArbEventContent.arbUserMetaData
import app.cash.nostrino.model.ArbTags.arbHashTag
import app.cash.nostrino.model.EncryptedDm
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.EventDeletion
import app.cash.nostrino.model.Filter
import app.cash.turbine.test
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.set
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RelayClientTest : StringSpec({

  "can connect to local relay, publish and consume notes" {
    val sec = SecKeyGenerator().generate()
    val note = arbTextNote.next()
    val event = note.sign(sec)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.userNotes(sec.pubKey))
      send(event)

      notes.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe note.text
          this.content() shouldBe note
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can publish and consume an event of a kind nostrino has no content type for" {
    val sec = SecKeyGenerator().generate()
    val event = Event.sign(
      sec = sec,
      kind = 1337,
      tags = listOf(listOf("client", "nostrino"), listOf("t", "raw")),
      content = "line\u2028separator",
    )

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter(authors = setOf(sec.pubKey.key.hex()), kinds = setOf(event.kind)))
      send(event)

      allEvents.test {
        with(awaitItem()) {
          this shouldBe event
          this.validId shouldBe true
          this.validSignature shouldBe true
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can publish and consume DMs" {
    val alice = SecKeyGenerator().generate()
    val bob = SecKeyGenerator().generate()

    val dm = EncryptedDm(bob, alice.pubKey, "Hello world")
    val event = dm.sign(bob)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.directMessages(alice.pubKey))
      send(event)

      directMessages.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe dm.cipherText.toString()
          this.content() shouldBe dm
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can publish and consume reactions the publishing user" {
    val alice = SecKeyGenerator().generate()

    val reaction = arbReaction.next()
    val event = reaction.sign(alice)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.reactions(author = alice.pubKey))
      send(event)

      reactions.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe reaction.toJsonString()
          this.content() shouldBe reaction
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can consume reactions for an event" {
    val alice = SecKeyGenerator().generate()

    val reaction = arbReaction.next()
    val event = reaction.sign(alice)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.reactions(eventId = reaction.eventId))
      send(event)

      reactions.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe reaction.toJsonString()
          this.content() shouldBe reaction
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can consume reactions to an event author" {
    val alice = SecKeyGenerator().generate()

    val reaction = arbReaction.next()
    val event = reaction.sign(alice)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.reactions(eventAuthor = reaction.authorPubKey))
      send(event)

      reactions.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe reaction.toJsonString()
          this.content() shouldBe reaction
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can publish and consume user metadata" {
    val sec = SecKeyGenerator().generate()
    val md = arbUserMetaData.next()
    val event = md.sign(sec)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(Filter.userMetaData(sec.pubKey))
      send(event)

      userMetaData.test {
        with(awaitItem()) {
          this shouldBe event
          this.content shouldBe md.toJsonString()
          this.content() shouldBe md
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can consume events by hashtag" {
    val sec = SecKeyGenerator().generate()
    val noteWithoutHashTags = arbTextNote.next()
    val tags = Arb.set(arbHashTag, 4).next().toList()
    val noteWithHashTags = arbTextNote.next().copy(tags = tags)

    with(RelayClient("ws://localhost:7707")) {
      start()
      subscribe(
        Filter.hashTagNotes(
          hashtags = setOf(tags[1], tags[3])
        )
      )

      send(noteWithHashTags.sign(sec))
      send(noteWithoutHashTags.sign(sec))

      notes.test {
        with(awaitItem()) {
          this.content shouldBe noteWithHashTags.text
          this.content() shouldBe noteWithHashTags
        }
        expectNoEvents()
      }

      stop()
    }
  }

  "can issue event deletion requests" {
    val sec = SecKeyGenerator().generate()
    val note = arbTextNote.next().sign(sec)
    val deletion = EventDeletion("redact!", setOf(note.id))

    with(RelayClient("ws://localhost:7707")) {
      start()
      send(note)
      send(deletion.sign(sec))

      subscribe(Filter.userNotes(sec.pubKey))

      notes.test {
        expectNoEvents()
      }

      stop()
    }
  }

  "can unsubscribe via the returned subscription" {
    val alice = SecKeyGenerator().generate()
    val bob = SecKeyGenerator().generate()

    with(RelayClient("ws://localhost:7707")) {
      start()

      // create two separate subscriptions, one for each key
      subscribe(Filter.userNotes(alice.pubKey))
      val bobSub = subscribe(Filter.userNotes(bob.pubKey))

      // start counting how many events we receive from each key
      var (aliceCount, bobCount) = 0 to 0
      val readingJob = launch {
        notes.collect {
          when (it.pubKey) {
            alice.pubKey.key -> aliceCount++
            bob.pubKey.key -> bobCount++
            else -> fail("Unrecognised key in event: $it")
          }
        }
      }

      // start sending events from both alice and bob
      val sendingJob = launch {
        while (true) {
          send(arbTextNote.next().sign(alice))
          send(arbTextNote.next().sign(bob))
          delay(30L)
        }
      }

      // allow some time for events to be collected
      delay(200L)

      // unsubscribe from bob
      unsubscribe(bobSub)

      // allow some time for more events
      delay(300L)

      // the count of alice's messages must exceed bob's by > 2X
      bobCount * 2 shouldBeLessThan aliceCount

      sendingJob.cancel()
      readingJob.cancel()
      stop()
    }
  }
})
