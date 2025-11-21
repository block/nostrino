package app.cash.nostrino.model.nip47

import app.cash.nostrino.model.EncryptionTag
import app.cash.nostrino.model.EventContent
import app.cash.nostrino.model.NotificationsTag
import app.cash.nostrino.model.Tag

/**
 * NIP-47 info event. Event kind 13194, as defined in
 * [nip-47](https://github.com/nostr-protocol/nips/blob/master/47.md).
 *
 * Published by wallet service to indicate supported capabilities and encryption schemes.
 */
data class Nip47Info(
  val capabilities: Set<String>,
  val supportedEncryption: Set<String>,
  val notifications: Set<String>?,
  override val tags: List<Tag> = buildTags(supportedEncryption, notifications)
) : EventContent {

  override val kind = Companion.kind

  override fun toJsonString() = capabilities.joinToString(" ")

  companion object {
    const val kind = 13194

    private fun buildTags(
      supportedEncryption: Set<String>,
      notifications: Set<String>?
    ): List<Tag> = buildList {
      if (supportedEncryption.isNotEmpty()) {
        add(EncryptionTag(supportedEncryption.joinToString(" ")))
      }
      if (notifications != null && notifications.isNotEmpty()) {
        add(NotificationsTag(notifications.joinToString(" ")))
      }
    }
  }
}
