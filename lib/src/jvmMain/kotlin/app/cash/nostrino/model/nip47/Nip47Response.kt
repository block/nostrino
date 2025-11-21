package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.model.EventContent
import app.cash.nostrino.model.EventTag
import app.cash.nostrino.model.PubKeyTag
import app.cash.nostrino.model.Tag
import okio.ByteString

/**
 * NIP-47 response event. Event kind 23195, as defined in
 * [nip-47](https://github.com/nostr-protocol/nips/blob/master/47.md).
 *
 * Sent by wallet service to client with encrypted response payload.
 */
data class Nip47Response(
  val clientPubKey: PubKey,
  val requestEventId: ByteString,
  val encryptedContent: String,
  override val tags: List<Tag> = buildTags(clientPubKey, requestEventId)
) : EventContent {

  override val kind = Companion.kind

  override fun toJsonString() = encryptedContent

  companion object {
    const val kind = 23195

    private fun buildTags(clientPubKey: PubKey, requestEventId: ByteString): List<Tag> = listOf(
      PubKeyTag(clientPubKey),
      EventTag(requestEventId)
    )
  }
}
