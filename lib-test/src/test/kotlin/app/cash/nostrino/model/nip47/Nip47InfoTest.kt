package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.nip47.ArbNip47.arbNip47Info
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next

class Nip47InfoTest : StringSpec({
  "round trip sign and parse" {
    val info = arbNip47Info.next()
    val signed = info.sign(arbSecKey.next())

    signed.validSignature shouldBe true
    signed.kind shouldBe Nip47Info.kind
  }

  "capabilities are space-separated in content" {
    val info = Nip47Info(
      capabilities = setOf("pay_invoice", "get_balance", "make_invoice"),
      supportedEncryption = setOf("nip44_v2"),
      notifications = null
    )

    info.toJsonString() shouldBe "pay_invoice get_balance make_invoice"
  }

  "tags include encryption when present" {
    val info = Nip47Info(
      capabilities = setOf("pay_invoice"),
      supportedEncryption = setOf("nip44_v2", "nip04"),
      notifications = null
    )

    val encryptionTag = info.tags.filterIsInstance<app.cash.nostrino.model.EncryptionTag>().firstOrNull()
    encryptionTag?.encryptionSchemes shouldBe "nip44_v2 nip04"
  }

  "tags include notifications when present" {
    val info = Nip47Info(
      capabilities = setOf("pay_invoice"),
      supportedEncryption = setOf("nip04"),
      notifications = setOf("payment_received", "payment_sent")
    )

    val notificationsTag = info.tags.filterIsInstance<app.cash.nostrino.model.NotificationsTag>().firstOrNull()
    notificationsTag?.notificationTypes shouldBe "payment_received payment_sent"
  }

  "kind is 13194" {
    Nip47Info.kind shouldBe 13194
  }
})
