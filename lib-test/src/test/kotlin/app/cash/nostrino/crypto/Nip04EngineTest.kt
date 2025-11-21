package app.cash.nostrino.crypto

import app.cash.nostrino.crypto.ArbKeys.arbSecKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next

class Nip04EngineTest : StringSpec({
  "encrypts and decrypts messages" {
    val alice = arbSecKey.next()
    val bob = arbSecKey.next()
    val message = "Hello Bob, this is Alice!"

    val encrypted = Nip04Engine.encrypt(alice, bob.pubKey.key, message)
    val decrypted = Nip04Engine.decrypt(bob, alice.pubKey.key, encrypted)

    decrypted shouldBe message
  }

  "engine name is nip04" {
    Nip04Engine.name shouldBe "nip04"
  }

  "round trip with different keys produces different ciphertext" {
    val alice = arbSecKey.next()
    val bob = arbSecKey.next()
    val message = "Secret message"

    val encrypted1 = Nip04Engine.encrypt(alice, bob.pubKey.key, message)
    val encrypted2 = Nip04Engine.encrypt(alice, bob.pubKey.key, message)

    // Different IVs should produce different ciphertext
    // (though they decrypt to the same plaintext)
    val decrypted1 = Nip04Engine.decrypt(bob, alice.pubKey.key, encrypted1)
    val decrypted2 = Nip04Engine.decrypt(bob, alice.pubKey.key, encrypted2)

    decrypted1 shouldBe message
    decrypted2 shouldBe message
  }

  "decryption with wrong key fails" {
    val alice = arbSecKey.next()
    val bob = arbSecKey.next()
    val eve = arbSecKey.next()
    val message = "Secret for Bob only"

    val encrypted = Nip04Engine.encrypt(alice, bob.pubKey.key, message)

    // Eve cannot decrypt message intended for Bob
    val result = runCatching {
      Nip04Engine.decrypt(eve, alice.pubKey.key, encrypted)
    }

    result.isFailure shouldBe true
  }
})
