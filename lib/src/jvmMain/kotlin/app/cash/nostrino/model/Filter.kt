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

import app.cash.nostrino.crypto.PubKey
import okio.ByteString
import java.time.Instant
import kotlin.time.Duration.Companion.hours

/**
 * A subscription filter, as defined in
 * [nip-01](https://github.com/nostr-protocol/nips/blob/master/01.md#from-client-to-relay-sending-events-and-creating-subscriptions).
 */
data class Filter(
  val ids: Set<String>? = null,
  val since: Instant? = null,
  val authors: Set<String>? = null,
  val kinds: Set<Int>? = null,
  val eTags: Set<String>? = null,
  val pTags: Set<String>? = null,
  val tTags: Set<String>? = null,
  val limit: Int? = null
) {

  /** Add additional author public keys to this filter */
  fun plusAuthors(vararg authors: PubKey) = copy(
    authors = authors.map { it.key.hex() }.plus(this.authors ?: emptySet()).toSet()
  )

  companion object {

    /** All text notes. */
    val globalFeedNotes = Filter(
      since = Instant.now().minusSeconds(12.hours.inWholeSeconds),
      kinds = setOf(TextNote.kind),
      limit = 500
    )

    /** Text notes authored by the given public key only */
    fun userNotes(author: PubKey, since: Instant = Instant.EPOCH) = userNotes(
      authors = setOf(author),
      since = since
    )

    /** Text notes authored by any of the public keys provided */
    fun userNotes(authors: Set<PubKey>, since: Instant = Instant.EPOCH) = Filter(
      since = since,
      authors = authors.map { it.key.hex() }.toSet(),
      kinds = setOf(TextNote.kind),
      limit = 500
    )

    /** Direct messages authored by the provided public key */
    fun directMessages(pubKey: PubKey, since: Instant = Instant.EPOCH) = Filter(
      since = since,
      kinds = setOf(EncryptedDm.kind),
      pTags = setOf(pubKey.key.hex())
    )

    /** MetaData of the given public key */
    fun userMetaData(pubKey: PubKey, since: Instant = Instant.EPOCH) = Filter(
      since = since,
      kinds = setOf(UserMetaData.kind),
      authors = setOf(pubKey.key.hex())
    )

    fun hashTagNotes(
      hashtags: Set<HashTag>,
      since: Instant = Instant.EPOCH,
      limit: Int = 500,
    ) = Filter(
      since = since,
      kinds = setOf(TextNote.kind),
      tTags = hashtags.map { it.label }.toSet(),
      limit = limit
    )

    /**
     * Reactions authored by the given public key
     * and/or associated with the given event
     * and/or in response to the given public key.
     */
    fun reactions(
      author: PubKey? = null,
      eventId: ByteString? = null,
      eventAuthor: PubKey? = null,
      since: Instant = Instant.EPOCH
    ) = Filter(
      since = since,
      kinds = setOf(Reaction.kind),
      authors = author?.let { setOf(it.key.hex()) },
      pTags = eventAuthor?.let { setOf(it.key.hex()) },
      eTags = eventId?.let { setOf(it.hex()) },
    )
  }
}
