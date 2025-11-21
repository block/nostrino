package app.cash.nostrino.model.nip47

import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.nip47.ArbNip47.arbNip47Request
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next

class Nip47RequestTest : StringSpec({
  "round trip sign and parse" {
    val request = arbNip47Request.next()
    val signed = request.sign(arbSecKey.next())

    signed.validSignature shouldBe true
    signed.kind shouldBe Nip47Request.kind
  }

  "encrypted content is in content field" {
    val request = Nip47Request(
      walletPubKey = arbPubKey.next(),
      encryptedContent = "encrypted_payload_here",
      encryptionType = "nip04"
    )

    request.toJsonString() shouldBe "encrypted_payload_here"
  }

  "tags include pubkey tag for wallet" {
    val walletPubKey = arbPubKey.next()
    val request = Nip47Request(
      walletPubKey = walletPubKey,
      encryptedContent = "test",
      encryptionType = "nip04"
    )

    val pubKeyTag = request.tags.filterIsInstance<app.cash.nostrino.model.PubKeyTag>().firstOrNull()
    pubKeyTag?.pubKey shouldBe walletPubKey
  }

  "encryption tag included when not nip04" {
    val request = Nip47Request(
      walletPubKey = arbPubKey.next(),
      encryptedContent = "test",
      encryptionType = "nip44_v2"
    )

    val encryptionTag = request.tags.filterIsInstance<app.cash.nostrino.model.EncryptionTag>().firstOrNull()
    encryptionTag?.encryptionSchemes shouldBe "nip44_v2"
  }

  "encryption tag omitted when nip04" {
    val request = Nip47Request(
      walletPubKey = arbPubKey.next(),
      encryptedContent = "test",
      encryptionType = "nip04"
    )

    val encryptionTag = request.tags.filterIsInstance<app.cash.nostrino.model.EncryptionTag>().firstOrNull()
    encryptionTag shouldBe null
  }

  "kind is 23194" {
    Nip47Request.kind shouldBe 23194
  }
})
