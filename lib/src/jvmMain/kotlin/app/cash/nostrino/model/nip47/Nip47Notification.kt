package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.model.EventContent
import app.cash.nostrino.model.PubKeyTag
import app.cash.nostrino.model.Tag

/**
 * NIP-47 notification event. Event kind 23197, as defined in
 * [nip-47](https://github.com/nostr-protocol/nips/blob/master/47.md).
 *
 * Sent by wallet service to client with encrypted notification payload (NIP-44).
 */
data class Nip47Notification(
  val clientPubKey: PubKey,
  val encryptedContent: String,
  override val tags: List<Tag> = listOf(PubKeyTag(clientPubKey))
) : EventContent {

  override val kind = Companion.kind

  override fun toJsonString() = encryptedContent

  companion object {
    const val kind = 23197
  }
}

/**
 * NIP-47 notification event (legacy). Event kind 23196, as defined in
 * [nip-47](https://github.com/nostr-protocol/nips/blob/master/47.md).
 *
 * Sent by wallet service to client with encrypted notification payload (NIP-04).
 * Deprecated in favor of kind 23197 with NIP-44 encryption.
 */
data class Nip47NotificationLegacy(
  val clientPubKey: PubKey,
  val encryptedContent: String,
  override val tags: List<Tag> = listOf(PubKeyTag(clientPubKey))
) : EventContent {

  override val kind = Companion.kind

  override fun toJsonString() = encryptedContent

  companion object {
    const val kind = 23196
  }
}
