package app.cash.nostrino.model.nip47

import app.cash.nostrino.ArbPrimitive.arbByteString32
import app.cash.nostrino.ArbPrimitive.arbVanillaString
import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.set

object ArbNip47 {
  private val arbEncryptionScheme = Arb.enum<EncryptionScheme>()
  private val arbNotificationType = Arb.enum<NotificationType>()
  private val arbCapability = Arb.enum<Capability>()

  val arbNip47Info: Arb<Nip47Info> by lazy {
    Arb.bind(
      Arb.set(arbCapability, 1..5).map { it.map { c -> c.name.lowercase() }.toSet() },
      Arb.set(arbEncryptionScheme, 1..2).map { it.map { e -> e.value }.toSet() },
      Arb.set(arbNotificationType, 0..2).map { it.map { n -> n.value }.toSet() }.orNull()
    ) { capabilities, encryption, notifications ->
      Nip47Info(capabilities, encryption, notifications)
    }
  }

  val arbNip47Request: Arb<Nip47Request> by lazy {
    Arb.bind(
      arbPubKey,
      arbVanillaString,
      arbEncryptionScheme.map { it.value }
    ) { walletPubKey, encryptedContent, encryptionType ->
      Nip47Request(walletPubKey, encryptedContent, encryptionType)
    }
  }

  val arbNip47Response: Arb<Nip47Response> by lazy {
    Arb.bind(
      arbPubKey,
      arbByteString32,
      arbVanillaString
    ) { clientPubKey, requestEventId, encryptedContent ->
      Nip47Response(clientPubKey, requestEventId, encryptedContent)
    }
  }

  val arbNip47Notification: Arb<Nip47Notification> by lazy {
    Arb.bind(
      arbPubKey,
      arbVanillaString
    ) { clientPubKey, encryptedContent ->
      Nip47Notification(clientPubKey, encryptedContent)
    }
  }

  val arbNip47NotificationLegacy: Arb<Nip47NotificationLegacy> by lazy {
    Arb.bind(
      arbPubKey,
      arbVanillaString
    ) { clientPubKey, encryptedContent ->
      Nip47NotificationLegacy(clientPubKey, encryptedContent)
    }
  }

  private enum class EncryptionScheme(val value: String) {
    NIP04("nip04"),
    NIP44_V2("nip44_v2")
  }

  private enum class NotificationType(val value: String) {
    PAYMENT_RECEIVED("payment_received"),
    PAYMENT_SENT("payment_sent")
  }

  private enum class Capability {
    PAY_INVOICE,
    GET_BALANCE,
    MAKE_INVOICE,
    LOOKUP_INVOICE,
    LIST_TRANSACTIONS,
    GET_INFO,
    PAY_KEYSEND,
    MULTI_PAY_INVOICE,
    MULTI_PAY_KEYSEND,
    NOTIFICATIONS
  }
}
