package app.cash.nostrino.crypto

import okio.ByteString

/**
 * Encryption engine interface for Nostr end-to-end encrypted messages.
 * Implementations provide different encryption schemes (NIP-04, NIP-44, etc.).
 */
interface EncryptionEngine {
  /**
   * Name of the encryption scheme (e.g., "nip04", "nip44_v2").
   * Used in the "encryption" tag of NIP-47 events.
   */
  val name: String

  /**
   * Encrypt plaintext for a recipient using their public key.
   *
   * @param myKey sender's secret key
   * @param theirPubKey recipient's public key
   * @param plaintext message to encrypt
   * @return encrypted ciphertext as string
   */
  fun encrypt(myKey: SecKey, theirPubKey: ByteString, plaintext: String): String

  /**
   * Decrypt ciphertext from a sender using their public key.
   *
   * @param myKey recipient's secret key
   * @param theirPubKey sender's public key
   * @param ciphertext encrypted message
   * @return decrypted plaintext
   */
  fun decrypt(myKey: SecKey, theirPubKey: ByteString, ciphertext: String): String
}
