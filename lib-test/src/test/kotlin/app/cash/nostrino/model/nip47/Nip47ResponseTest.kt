package app.cash.nostrino.model.nip47

import app.cash.nostrino.ArbPrimitive.arbByteString32
import app.cash.nostrino.crypto.ArbKeys.arbPubKey
import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import app.cash.nostrino.model.nip47.ArbNip47.arbNip47Response
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next

class Nip47ResponseTest : StringSpec({
  "round trip sign and parse" {
    val response = arbNip47Response.next()
    val signed = response.sign(arbSecKey.next())

    signed.validSignature shouldBe true
    signed.kind shouldBe Nip47Response.kind
  }

  "encrypted content is in content field" {
    val response = Nip47Response(
      clientPubKey = arbPubKey.next(),
      requestEventId = arbByteString32.next(),
      encryptedContent = "encrypted_response_payload"
    )

    response.toJsonString() shouldBe "encrypted_response_payload"
  }

  "tags include pubkey tag for client" {
    val clientPubKey = arbPubKey.next()
    val response = Nip47Response(
      clientPubKey = clientPubKey,
      requestEventId = arbByteString32.next(),
      encryptedContent = "test"
    )

    val pubKeyTag = response.tags.filterIsInstance<app.cash.nostrino.model.PubKeyTag>().firstOrNull()
    pubKeyTag?.pubKey shouldBe clientPubKey
  }

  "tags include event tag for request" {
    val requestEventId = arbByteString32.next()
    val response = Nip47Response(
      clientPubKey = arbPubKey.next(),
      requestEventId = requestEventId,
      encryptedContent = "test"
    )

    val eventTag = response.tags.filterIsInstance<app.cash.nostrino.model.EventTag>().firstOrNull()
    eventTag?.eventId shouldBe requestEventId
  }

  "kind is 23195" {
    Nip47Response.kind shouldBe 23195
  }
})
