package app.cash.nostrino.crypto

import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-04 encryption engine using AES-256-CBC.
 * This is the original Nostr encryption scheme, deprecated in favor of NIP-44.
 *
 * See: https://github.com/nostr-protocol/nips/blob/master/04.md
 */
object Nip04Engine : EncryptionEngine {
  override val name = "nip04"

  override fun encrypt(myKey: SecKey, theirPubKey: ByteString, plaintext: String): String {
    val random = SecureRandom()
    val iv = ByteArray(16)
    random.nextBytes(iv)

    val sharedSecret = myKey.sharedSecretWith(PubKey(theirPubKey))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))

    val encrypted = cipher.doFinal(plaintext.toByteArray())
    val cipherText = CipherText(encrypted.toByteString(), iv.toByteString())
    return cipherText.toString()
  }

  override fun decrypt(myKey: SecKey, theirPubKey: ByteString, ciphertext: String): String {
    val cipherText = CipherText.parse(ciphertext)
    return cipherText.decipher(PubKey(theirPubKey), myKey)
  }
}
