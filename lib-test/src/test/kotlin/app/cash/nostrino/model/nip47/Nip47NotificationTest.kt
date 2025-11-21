package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.nip47.ArbNip47.arbNip47Notification
import app.cash.nostrino.model.nip47.ArbNip47.arbNip47NotificationLegacy
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next

class Nip47NotificationTest : StringSpec({
  "notification round trip sign and parse" {
    val notification = arbNip47Notification.next()
    val signed = notification.sign(arbSecKey.next())

    signed.validSignature shouldBe true
    signed.kind shouldBe Nip47Notification.kind
  }

  "notification legacy round trip sign and parse" {
    val notification = arbNip47NotificationLegacy.next()
    val signed = notification.sign(arbSecKey.next())

    signed.validSignature shouldBe true
    signed.kind shouldBe Nip47NotificationLegacy.kind
  }

  "notification encrypted content is in content field" {
    val notification = Nip47Notification(
      clientPubKey = arbPubKey.next(),
      encryptedContent = "encrypted_notification_payload"
    )

    notification.toJsonString() shouldBe "encrypted_notification_payload"
  }

  "notification tags include pubkey tag for client" {
    val clientPubKey = arbPubKey.next()
    val notification = Nip47Notification(
      clientPubKey = clientPubKey,
      encryptedContent = "test"
    )

    val pubKeyTag = notification.tags.filterIsInstance<app.cash.nostrino.model.PubKeyTag>().firstOrNull()
    pubKeyTag?.pubKey shouldBe clientPubKey
  }

  "notification kind is 23197" {
    Nip47Notification.kind shouldBe 23197
  }

  "notification legacy kind is 23196" {
    Nip47NotificationLegacy.kind shouldBe 23196
  }
})
