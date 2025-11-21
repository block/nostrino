package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.model.EncryptionTag
import app.cash.nostrino.model.EventContent
import app.cash.nostrino.model.PubKeyTag
import app.cash.nostrino.model.Tag

/**
 * NIP-47 request event. Event kind 23194, as defined in
 * [nip-47](https://github.com/nostr-protocol/nips/blob/master/47.md).
 *
 * Sent by client to wallet service with encrypted command payload.
 */
data class Nip47Request(
  val walletPubKey: PubKey,
  val encryptedContent: String,
  val encryptionType: String = "nip04",
  override val tags: List<Tag> = buildTags(walletPubKey, encryptionType)
) : EventContent {

  override val kind = Companion.kind

  override fun toJsonString() = encryptedContent

  companion object {
    const val kind = 23194

    private fun buildTags(walletPubKey: PubKey, encryptionType: String): List<Tag> = buildList {
      add(PubKeyTag(walletPubKey))
      if (encryptionType != "nip04") {
        add(EncryptionTag(encryptionType))
      }
    }
  }
}
