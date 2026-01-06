# NIP-47 (Nostr Wallet Connect) Implementation Plan

## Overview

Implementing NIP-47 protocol layer for Nostr Wallet Connect, enabling clients and services to exchange Lightning wallet commands/responses through encrypted Nostr events. This implementation provides the protocol primitives only - actual Lightning wallet integration is left to SDK consumers.

## Current State Analysis

### What Exists:
- ✅ NIP-44 encryption ([Nip44.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44.kt))
- ✅ NIP-04 encryption ([CipherText.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/CipherText.kt), [EncryptedDm.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/EncryptedDm.kt))
- ✅ Event system with kind-based polymorphic deserialization ([Event.kt:53-83](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt#L53-L83))
- ✅ Tag sealed interface pattern ([Tag.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/Tag.kt))
- ✅ Moshi adapters for complex JSON structures ([UserMetaData.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/UserMetaData.kt))
- ✅ RelayClient for WebSocket communication

### What's Missing:
- Event kinds: 13194 (Info), 23194 (Request), 23195 (Response), 23197 (Notification NIP-44)
- Tags: encryption, notifications, d
- Request/response/notification data models
- Connection URI parser (`nostr+walletconnect://`)
- Dual encryption support (NIP-04/NIP-44) in NIP-47 events

## Desired End State

A complete NIP-47 protocol implementation with:
- Both client and service side event models
- Backward-compatible dual encryption (NIP-04 and NIP-44)
- Type-safe request/response/notification structures
- Connection URI parsing
- Full integration tests against Docker relay

### Verification:
- `bin/gradle build` passes with all tests
- `bin/gradle apiCheck` passes
- Integration tests demonstrate client-service roundtrip communication
- Both NIP-04 and NIP-44 encryption work in tests

## What We're NOT Doing

- Lightning wallet implementation (LND, CLN, etc.)
- Payment processing logic
- Invoice generation/validation
- Balance management
- Transaction storage
- Connection/pairing UI
- Multi-relay coordination (will use existing RelayClient/RelaySet)

## Implementation Approach

Follow established patterns from existing NIPs (especially ZapRequest/ZapReceipt for complex events). Build incrementally in phases, testing each layer before moving forward. Use Moshi for complex JSON in request/response payloads. Support both NIP-04 and NIP-44 encryption with tag-based negotiation.

---

## Phase 1: Foundation - Tags & Base Types

### Overview
Add new tag types and base data structures needed for NIP-47 events.

### Changes Required:

#### 1. New Tag Types
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/Tag.kt`

**Changes**: Add three new tag data classes and update `parseRaw()`

```kotlin
data class EncryptionTag(val methods: List<String>) : Tag {
  override fun toJsonList() = listOf("encryption") + methods
}

data class NotificationsTag(val types: List<String>) : Tag {
  override fun toJsonList() = listOf("notifications") + types
}

data class DTag(val identifier: String) : Tag {
  override fun toJsonList() = listOf("d", identifier)
}
```

Update `parseRaw()` companion object:
```kotlin
when (tag) {
  // ... existing tags ...
  "encryption" -> EncryptionTag(values)
  "notifications" -> NotificationsTag(values)
  "d" -> DTag(value)
  else -> throw IllegalArgumentException("Invalid tag format: $strings")
}
```

#### 2. NIP-47 Data Models Package
**File**: `lib/src/commonMain/kotlin/app/cash/nostrino/nip47/WalletConnectModels.kt`

**Changes**: Create data classes for request/response/notification payloads

```kotlin
package app.cash.nostrino.nip47

import com.squareup.moshi.Json

sealed interface WalletConnectPayload

data class WalletRequest(
  val method: String,
  val params: Map<String, Any>
) : WalletConnectPayload

data class WalletResponse(
  @Json(name = "result_type")
  val resultType: String,
  val error: WalletError? = null,
  val result: Map<String, Any>? = null
) : WalletConnectPayload {
  init {
    require((error == null) != (result == null)) {
      "Either error or result must be present, but not both"
    }
  }
}

data class WalletError(
  val code: String,
  val message: String
)

data class WalletNotification(
  @Json(name = "notification_type")
  val notificationType: String,
  val notification: Map<String, Any>
) : WalletConnectPayload

enum class EncryptionMethod(val value: String) {
  NIP04("nip04"),
  NIP44_V2("nip44_v2");
  
  companion object {
    fun fromString(value: String): EncryptionMethod? =
      values().find { it.value == value }
  }
}
```

#### 3. Connection URI Parser
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/nip47/WalletConnectUri.kt`

**Changes**: Parse `nostr+walletconnect://` URIs

```kotlin
package app.cash.nostrino.nip47

import app.cash.nostrino.crypto.PubKey
import okio.ByteString.Companion.decodeHex
import java.net.URI
import java.net.URLDecoder

data class WalletConnectUri(
  val walletPubKey: PubKey,
  val relayUrls: List<String>,
  val secret: ByteArray,
  val lud16: String? = null
) {
  companion object {
    fun parse(uri: String): WalletConnectUri {
      require(uri.startsWith("nostr+walletconnect://")) {
        "Invalid URI scheme: must start with nostr+walletconnect://"
      }
      
      val javaUri = URI(uri.replaceFirst("nostr+walletconnect://", "http://"))
      val pubKey = PubKey(javaUri.host.decodeHex())
      
      val params = javaUri.query.split("&")
        .associate { param ->
          val (key, value) = param.split("=", limit = 2)
          URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }
      
      val relayUrls = params["relay"]?.split(",") ?: emptyList()
      val secret = params["secret"]?.decodeHex()?.toByteArray()
        ?: throw IllegalArgumentException("Missing required parameter: secret")
      
      require(secret.size == 32) { "Secret must be 32 bytes" }
      
      return WalletConnectUri(
        walletPubKey = pubKey,
        relayUrls = relayUrls,
        secret = secret,
        lud16 = params["lud16"]
      )
    }
  }
  
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WalletConnectUri) return false
    if (walletPubKey != other.walletPubKey) return false
    if (relayUrls != other.relayUrls) return false
    if (!secret.contentEquals(other.secret)) return false
    if (lud16 != other.lud16) return false
    return true
  }
  
  override fun hashCode(): Int {
    var result = walletPubKey.hashCode()
    result = 31 * result + relayUrls.hashCode()
    result = 31 * result + secret.contentHashCode()
    result = 31 * result + (lud16?.hashCode() ?: 0)
    return result
  }
}
```

### Success Criteria:

#### Automated Verification:
- [ ] All new code compiles: `bin/gradle build`
- [ ] No API compatibility issues: `bin/gradle apiCheck`
- [ ] Unit tests pass for URI parser
- [ ] Unit tests pass for tag serialization/deserialization

#### Manual Verification:
- [ ] Tags serialize to correct JSON format
- [ ] URI parser handles all parameter combinations correctly
- [ ] Error messages are clear for invalid inputs

**Implementation Note**: After completing this phase and all automated verification passes, pause for manual confirmation before proceeding to Phase 2.

---

## Phase 2: Event Models - Info Event (Kind 13194)

### Overview
Implement the WalletServiceInfo event that wallet services publish to advertise capabilities.

### Changes Required:

#### 1. WalletServiceInfo EventContent
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/WalletServiceInfo.kt`

**Changes**: Create new EventContent implementation

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.nip47.EncryptionMethod
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import com.squareup.moshi.Json

data class WalletServiceInfo(
  val commands: List<String>,
  @Json(name = "encryption_methods")
  val encryptionMethods: List<String>,
  val notifications: List<String>,
  override val tags: List<Tag> = listOfNotNull(
    if (encryptionMethods.isNotEmpty()) EncryptionTag(encryptionMethods) else null,
    if (notifications.isNotEmpty()) NotificationsTag(notifications) else null
  )
) : EventContent {
  override val kind = Companion.kind
  
  override fun toJsonString() = adapter.toJson(this)
  
  fun supportsEncryption(method: EncryptionMethod): Boolean =
    encryptionMethods.contains(method.value)
  
  fun supportsCommand(command: String): Boolean =
    commands.contains(command)
  
  companion object {
    const val kind = 13194
    private val adapter by lazy { moshi.adapter(WalletServiceInfo::class.java) }
  }
}
```

#### 2. Register in Event.kt
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt`

**Changes**: Add to `content()` method and adapter map

In `content()` method:
```kotlin
WalletServiceInfo.kind -> {
  adapter.fromJson(content)!!.copy(tags = tags)
}
```

In companion object adapter map:
```kotlin
private val adapters = mapOf(
  UserMetaData.kind to moshi.adapter(UserMetaData::class.java),
  WalletServiceInfo.kind to moshi.adapter(WalletServiceInfo::class.java),
)
```

#### 3. Unit Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/model/WalletServiceInfoTest.kt`

**Changes**: Create comprehensive tests

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.nip47.EncryptionMethod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly

class WalletServiceInfoTest : StringSpec({
  "serializes to JSON with all fields" {
    val info = WalletServiceInfo(
      commands = listOf("pay_invoice", "get_balance"),
      encryptionMethods = listOf("nip44_v2", "nip04"),
      notifications = listOf("payment_received")
    )
    
    val json = info.toJsonString()
    json shouldBe """{"commands":["pay_invoice","get_balance"],"encryption_methods":["nip44_v2","nip04"],"notifications":["payment_received"]}"""
  }
  
  "creates valid signed event" {
    val secKey = SecKey.generate()
    val info = WalletServiceInfo(
      commands = listOf("pay_invoice"),
      encryptionMethods = listOf("nip44_v2"),
      notifications = emptyList()
    )
    
    val event = info.sign(secKey)
    event.kind shouldBe 13194
    event.validSignature shouldBe true
  }
  
  "supports encryption method check" {
    val info = WalletServiceInfo(
      commands = listOf("pay_invoice"),
      encryptionMethods = listOf("nip44_v2", "nip04"),
      notifications = emptyList()
    )
    
    info.supportsEncryption(EncryptionMethod.NIP44_V2) shouldBe true
    info.supportsEncryption(EncryptionMethod.NIP04) shouldBe true
  }
  
  "includes tags for encryption and notifications" {
    val info = WalletServiceInfo(
      commands = listOf("pay_invoice"),
      encryptionMethods = listOf("nip44_v2"),
      notifications = listOf("payment_received")
    )
    
    info.tags.size shouldBe 2
    info.tags.filterIsInstance<EncryptionTag>().first().methods shouldContainExactly listOf("nip44_v2")
    info.tags.filterIsInstance<NotificationsTag>().first().types shouldContainExactly listOf("payment_received")
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] Tests pass: `bin/gradle test`
- [ ] Build succeeds: `bin/gradle build`
- [ ] API check passes: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] Event serialization produces valid JSON
- [ ] Signed events have correct kind and valid signatures
- [ ] Tag extraction works correctly

**Implementation Note**: After completing this phase and all automated verification passes, pause for manual confirmation before proceeding to Phase 3.

---

## Phase 3: Event Models - Request/Response (Kinds 23194, 23195)

### Overview
Implement encrypted request and response events with dual encryption support.

### Changes Required:

#### 1. Dual Encryption Helper
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/nip47/Nip47Encryption.kt`

**Changes**: Create encryption/decryption utilities supporting both methods

```kotlin
package app.cash.nostrino.nip47

import app.cash.nostrino.crypto.CipherText
import app.cash.nostrino.crypto.Nip44
import app.cash.nostrino.crypto.Nip44CipherText
import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.crypto.SecKey

object Nip47Encryption {
  fun encrypt(
    plaintext: String,
    from: SecKey,
    to: PubKey,
    method: EncryptionMethod
  ): String = when (method) {
    EncryptionMethod.NIP04 -> from.encrypt(to, plaintext).toString()
    EncryptionMethod.NIP44_V2 -> {
      val sharedSecret = from.sharedSecretWith(to)
      val conversationKey = Nip44.getConversationKey(sharedSecret)
      Nip44.encrypt(plaintext, conversationKey).toBase64()
    }
  }
  
  fun decrypt(
    ciphertext: String,
    from: PubKey,
    to: SecKey,
    method: EncryptionMethod
  ): String = when (method) {
    EncryptionMethod.NIP04 -> CipherText.parse(ciphertext).decipher(from, to)
    EncryptionMethod.NIP44_V2 -> {
      val sharedSecret = to.sharedSecretWith(from)
      val conversationKey = Nip44.getConversationKey(sharedSecret)
      val payload = Nip44CipherText.parse(ciphertext)
      Nip44.decrypt(payload, conversationKey)
    }
  }
}
```

#### 2. WalletRequest EventContent
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/WalletRequest.kt`

**Changes**: Create encrypted request event

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.nip47.EncryptionMethod
import app.cash.nostrino.nip47.Nip47Encryption
import app.cash.nostrino.protocol.serde.NostrJson.moshi

data class WalletRequest(
  val walletService: PubKey,
  val encryptedContent: String,
  val encryptionMethod: EncryptionMethod,
  override val tags: List<Tag> = listOf(
    PubKeyTag(walletService),
    EncryptionTag(listOf(encryptionMethod.value))
  )
) : EventContent {
  
  constructor(
    from: SecKey,
    to: PubKey,
    request: app.cash.nostrino.nip47.WalletRequest,
    encryptionMethod: EncryptionMethod = EncryptionMethod.NIP44_V2
  ) : this(
    walletService = to,
    encryptedContent = Nip47Encryption.encrypt(
      adapter.toJson(request),
      from,
      to,
      encryptionMethod
    ),
    encryptionMethod = encryptionMethod
  )
  
  override val kind = Companion.kind
  
  override fun toJsonString() = encryptedContent
  
  fun decrypt(from: PubKey, to: SecKey): app.cash.nostrino.nip47.WalletRequest {
    val json = Nip47Encryption.decrypt(encryptedContent, from, to, encryptionMethod)
    return adapter.fromJson(json)!!
  }
  
  companion object {
    const val kind = 23194
    private val adapter by lazy {
      moshi.adapter(app.cash.nostrino.nip47.WalletRequest::class.java)
    }
  }
}
```

#### 3. WalletResponse EventContent
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/WalletResponse.kt`

**Changes**: Create encrypted response event

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.nip47.EncryptionMethod
import app.cash.nostrino.nip47.Nip47Encryption
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import okio.ByteString

data class WalletResponse(
  val client: PubKey,
  val requestEventId: ByteString,
  val encryptedContent: String,
  val encryptionMethod: EncryptionMethod,
  override val tags: List<Tag> = listOf(
    PubKeyTag(client),
    EventTag(requestEventId),
    EncryptionTag(listOf(encryptionMethod.value))
  )
) : EventContent {
  
  constructor(
    from: SecKey,
    to: PubKey,
    requestEventId: ByteString,
    response: app.cash.nostrino.nip47.WalletResponse,
    encryptionMethod: EncryptionMethod = EncryptionMethod.NIP44_V2
  ) : this(
    client = to,
    requestEventId = requestEventId,
    encryptedContent = Nip47Encryption.encrypt(
      adapter.toJson(response),
      from,
      to,
      encryptionMethod
    ),
    encryptionMethod = encryptionMethod
  )
  
  override val kind = Companion.kind
  
  override fun toJsonString() = encryptedContent
  
  fun decrypt(from: PubKey, to: SecKey): app.cash.nostrino.nip47.WalletResponse {
    val json = Nip47Encryption.decrypt(encryptedContent, from, to, encryptionMethod)
    return adapter.fromJson(json)!!
  }
  
  companion object {
    const val kind = 23195
    private val adapter by lazy {
      moshi.adapter(app.cash.nostrino.nip47.WalletResponse::class.java)
    }
  }
}
```

#### 4. Register in Event.kt
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt`

**Changes**: Add request/response to deserialization

In `content()` method:
```kotlin
WalletRequest.kind -> {
  val encryptionMethods = tags.filterIsInstance<EncryptionTag>().firstOrNull()?.methods ?: listOf("nip44_v2")
  val encryptionMethod = EncryptionMethod.fromString(encryptionMethods.first())
    ?: throw IllegalArgumentException("Unsupported encryption method: ${encryptionMethods.first()}")
  WalletRequest(taggedPubKeys.first(), content, encryptionMethod, tags)
}

WalletResponse.kind -> {
  val encryptionMethods = tags.filterIsInstance<EncryptionTag>().firstOrNull()?.methods ?: listOf("nip44_v2")
  val encryptionMethod = EncryptionMethod.fromString(encryptionMethods.first())
    ?: throw IllegalArgumentException("Unsupported encryption method: ${encryptionMethods.first()}")
  WalletResponse(taggedPubKeys.first(), taggedEventIds.first(), content, encryptionMethod, tags)
}
```

#### 5. Unit Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/nip47/Nip47EncryptionTest.kt`

**Changes**: Test both encryption methods

```kotlin
package app.cash.nostrino.nip47

import app.cash.nostrino.crypto.SecKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Nip47EncryptionTest : StringSpec({
  "encrypts and decrypts with NIP-44" {
    val alice = SecKey.generate()
    val bob = SecKey.generate()
    val plaintext = """{"method":"get_balance","params":{}}"""
    
    val encrypted = Nip47Encryption.encrypt(plaintext, alice, bob.pubKey, EncryptionMethod.NIP44_V2)
    val decrypted = Nip47Encryption.decrypt(encrypted, alice.pubKey, bob, EncryptionMethod.NIP44_V2)
    
    decrypted shouldBe plaintext
  }
  
  "encrypts and decrypts with NIP-04" {
    val alice = SecKey.generate()
    val bob = SecKey.generate()
    val plaintext = """{"method":"get_balance","params":{}}"""
    
    val encrypted = Nip47Encryption.encrypt(plaintext, alice, bob.pubKey, EncryptionMethod.NIP04)
    val decrypted = Nip47Encryption.decrypt(encrypted, alice.pubKey, bob, EncryptionMethod.NIP04)
    
    decrypted shouldBe plaintext
  }
})
```

**File**: `lib-test/src/test/kotlin/app/cash/nostrino/model/WalletRequestTest.kt`

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.nip47.EncryptionMethod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WalletRequestTest : StringSpec({
  "creates encrypted request with NIP-44" {
    val clientKey = SecKey.generate()
    val serviceKey = SecKey.generate()
    
    val request = app.cash.nostrino.nip47.WalletRequest(
      method = "get_balance",
      params = emptyMap()
    )
    
    val event = WalletRequest(clientKey, serviceKey.pubKey, request, EncryptionMethod.NIP44_V2)
      .sign(clientKey)
    
    event.kind shouldBe 23194
    event.validSignature shouldBe true
    
    val eventContent = event.content() as WalletRequest
    val decrypted = eventContent.decrypt(clientKey.pubKey, serviceKey)
    decrypted.method shouldBe "get_balance"
  }
  
  "creates encrypted request with NIP-04" {
    val clientKey = SecKey.generate()
    val serviceKey = SecKey.generate()
    
    val request = app.cash.nostrino.nip47.WalletRequest(
      method = "pay_invoice",
      params = mapOf("invoice" to "lnbc...")
    )
    
    val event = WalletRequest(clientKey, serviceKey.pubKey, request, EncryptionMethod.NIP04)
      .sign(clientKey)
    
    event.kind shouldBe 23194
    
    val eventContent = event.content() as WalletRequest
    val decrypted = eventContent.decrypt(clientKey.pubKey, serviceKey)
    decrypted.method shouldBe "pay_invoice"
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] Tests pass: `bin/gradle test`
- [ ] Build succeeds: `bin/gradle build`
- [ ] Both NIP-04 and NIP-44 encryption work in tests
- [ ] API check passes: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] Encrypted content is not readable without decryption
- [ ] Decryption produces correct JSON structure
- [ ] Tags correctly indicate encryption method

**Implementation Note**: After completing this phase and all automated verification passes, pause for manual confirmation before proceeding to Phase 4.

---

## Phase 4: Event Models - Notifications (Kind 23197)

### Overview
Implement wallet notification events (NIP-44 only, as NIP-47 spec recommends deprecating NIP-04 notifications).

### Changes Required:

#### 1. WalletNotification EventContent
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/WalletNotification.kt`

**Changes**: Create notification event

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.PubKey
import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.nip47.EncryptionMethod
import app.cash.nostrino.nip47.Nip47Encryption
import app.cash.nostrino.protocol.serde.NostrJson.moshi

data class WalletNotification(
  val client: PubKey,
  val encryptedContent: String,
  override val tags: List<Tag> = listOf(
    PubKeyTag(client),
    EncryptionTag(listOf(EncryptionMethod.NIP44_V2.value))
  )
) : EventContent {
  
  constructor(
    from: SecKey,
    to: PubKey,
    notification: app.cash.nostrino.nip47.WalletNotification
  ) : this(
    client = to,
    encryptedContent = Nip47Encryption.encrypt(
      adapter.toJson(notification),
      from,
      to,
      EncryptionMethod.NIP44_V2
    )
  )
  
  override val kind = Companion.kind
  
  override fun toJsonString() = encryptedContent
  
  fun decrypt(from: PubKey, to: SecKey): app.cash.nostrino.nip47.WalletNotification {
    val json = Nip47Encryption.decrypt(encryptedContent, from, to, EncryptionMethod.NIP44_V2)
    return adapter.fromJson(json)!!
  }
  
  companion object {
    const val kind = 23197
    private val adapter by lazy {
      moshi.adapter(app.cash.nostrino.nip47.WalletNotification::class.java)
    }
  }
}
```

#### 2. Register in Event.kt
**File**: `lib/src/jvmMain/kotlin/app/cash/nostrino/model/Event.kt`

**Changes**: Add to deserialization

```kotlin
WalletNotification.kind -> WalletNotification(taggedPubKeys.first(), content, tags)
```

#### 3. Unit Tests
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/model/WalletNotificationTest.kt`

```kotlin
package app.cash.nostrino.model

import app.cash.nostrino.crypto.SecKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WalletNotificationTest : StringSpec({
  "creates encrypted notification with NIP-44" {
    val serviceKey = SecKey.generate()
    val clientKey = SecKey.generate()
    
    val notification = app.cash.nostrino.nip47.WalletNotification(
      notificationType = "payment_received",
      notification = mapOf(
        "amount" to 1000,
        "payment_hash" to "abc123"
      )
    )
    
    val event = WalletNotification(serviceKey, clientKey.pubKey, notification)
      .sign(serviceKey)
    
    event.kind shouldBe 23197
    event.validSignature shouldBe true
    
    val eventContent = event.content() as WalletNotification
    val decrypted = eventContent.decrypt(serviceKey.pubKey, clientKey)
    decrypted.notificationType shouldBe "payment_received"
  }
})
```

### Success Criteria:

#### Automated Verification:
- [ ] Tests pass: `bin/gradle test`
- [ ] Build succeeds: `bin/gradle build`
- [ ] Notifications encrypt/decrypt correctly
- [ ] API check passes: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] Notification payloads are properly encrypted
- [ ] Only NIP-44 is supported (as per NIP-47 recommendation)

**Implementation Note**: After completing this phase and all automated verification passes, pause for manual confirmation before proceeding to Phase 5.

---

## Phase 5: Integration Tests & API Dump

### Overview
End-to-end integration tests demonstrating client-service communication through relay.

### Changes Required:

#### 1. Integration Test
**File**: `lib-test/src/test/kotlin/app/cash/nostrino/nip47/WalletConnectIntegrationTest.kt`

**Changes**: Create comprehensive integration test

```kotlin
package app.cash.nostrino.nip47

import app.cash.nostrino.client.RelayClient
import app.cash.nostrino.crypto.SecKey
import app.cash.nostrino.model.Filter
import app.cash.nostrino.model.Subscription
import app.cash.nostrino.model.WalletNotification
import app.cash.nostrino.model.WalletRequest
import app.cash.nostrino.model.WalletResponse
import app.cash.nostrino.model.WalletServiceInfo
import app.cash.nostrino.test.DockerRelay
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WalletConnectIntegrationTest : StringSpec({
  beforeSpec { DockerRelay.start() }
  afterSpec { DockerRelay.stop() }
  
  "complete client-service request-response flow with NIP-44" {
    val serviceKey = SecKey.generate()
    val clientKey = SecKey.generate()
    
    val serviceRelay = RelayClient("ws://localhost:7707")
    val clientRelay = RelayClient("ws://localhost:7707")
    
    serviceRelay.start()
    clientRelay.start()
    
    val info = WalletServiceInfo(
      commands = listOf("get_balance", "pay_invoice"),
      encryptionMethods = listOf("nip44_v2", "nip04"),
      notifications = listOf("payment_received")
    ).sign(serviceKey)
    
    serviceRelay.send(info)
    
    val serviceSubscription = serviceRelay.subscribe(
      setOf(Filter(kinds = setOf(23194), tags = mapOf("p" to setOf(serviceKey.pubKey.hex())))),
      Subscription.generate()
    )
    
    val request = WalletRequest(
      clientKey,
      serviceKey.pubKey,
      app.cash.nostrino.nip47.WalletRequest("get_balance", emptyMap()),
      EncryptionMethod.NIP44_V2
    ).sign(clientKey)
    
    val clientSubscription = clientRelay.subscribe(
      setOf(Filter(kinds = setOf(23195), tags = mapOf("p" to setOf(clientKey.pubKey.hex())))),
      Subscription.generate()
    )
    
    clientRelay.send(request)
    
    val receivedRequest = serviceRelay.allEvents
      .filter { it.kind == 23194 }
      .first()
    
    receivedRequest.validSignature shouldBe true
    val requestContent = receivedRequest.content() as WalletRequest
    val decryptedRequest = requestContent.decrypt(clientKey.pubKey, serviceKey)
    decryptedRequest.method shouldBe "get_balance"
    
    val response = WalletResponse(
      serviceKey,
      clientKey.pubKey,
      receivedRequest.id,
      app.cash.nostrino.nip47.WalletResponse(
        resultType = "get_balance",
        result = mapOf("balance" to 50000)
      ),
      EncryptionMethod.NIP44_V2
    ).sign(serviceKey)
    
    serviceRelay.send(response)
    
    val receivedResponse = clientRelay.allEvents
      .filter { it.kind == 23195 }
      .first()
    
    receivedResponse.validSignature shouldBe true
    val responseContent = receivedResponse.content() as WalletResponse
    val decryptedResponse = responseContent.decrypt(serviceKey.pubKey, clientKey)
    decryptedResponse.resultType shouldBe "get_balance"
    decryptedResponse.result?.get("balance") shouldBe 50000
    
    serviceRelay.stop()
    clientRelay.stop()
  }
  
  "service sends notification to client with NIP-44" {
    val serviceKey = SecKey.generate()
    val clientKey = SecKey.generate()
    
    val serviceRelay = RelayClient("ws://localhost:7707")
    val clientRelay = RelayClient("ws://localhost:7707")
    
    serviceRelay.start()
    clientRelay.start()
    
    val clientSubscription = clientRelay.subscribe(
      setOf(Filter(kinds = setOf(23197), tags = mapOf("p" to setOf(clientKey.pubKey.hex())))),
      Subscription.generate()
    )
    
    val notification = WalletNotification(
      serviceKey,
      clientKey.pubKey,
      app.cash.nostrino.nip47.WalletNotification(
        notificationType = "payment_received",
        notification = mapOf("amount" to 1000)
      )
    ).sign(serviceKey)
    
    serviceRelay.send(notification)
    
    val receivedNotification = clientRelay.allEvents
      .filter { it.kind == 23197 }
      .first()
    
    receivedNotification.validSignature shouldBe true
    val notificationContent = receivedNotification.content() as WalletNotification
    val decryptedNotification = notificationContent.decrypt(serviceKey.pubKey, clientKey)
    decryptedNotification.notificationType shouldBe "payment_received"
    
    serviceRelay.stop()
    clientRelay.stop()
  }
  
  "client-service flow with NIP-04 backward compatibility" {
    val serviceKey = SecKey.generate()
    val clientKey = SecKey.generate()
    
    val serviceRelay = RelayClient("ws://localhost:7707")
    val clientRelay = RelayClient("ws://localhost:7707")
    
    serviceRelay.start()
    clientRelay.start()
    
    val serviceSubscription = serviceRelay.subscribe(
      setOf(Filter(kinds = setOf(23194), tags = mapOf("p" to setOf(serviceKey.pubKey.hex())))),
      Subscription.generate()
    )
    
    val request = WalletRequest(
      clientKey,
      serviceKey.pubKey,
      app.cash.nostrino.nip47.WalletRequest("get_info", emptyMap()),
      EncryptionMethod.NIP04
    ).sign(clientKey)
    
    clientRelay.send(request)
    
    val receivedRequest = serviceRelay.allEvents
      .filter { it.kind == 23194 }
      .first()
    
    val requestContent = receivedRequest.content() as WalletRequest
    val decryptedRequest = requestContent.decrypt(clientKey.pubKey, serviceKey)
    decryptedRequest.method shouldBe "get_info"
    
    serviceRelay.stop()
    clientRelay.stop()
  }
})
```

#### 2. Update API Dump
**Changes**: Run API dump to capture new public API

```bash
bin/gradle apiDump
```

This will update `.api` files with new classes, methods, and constants.

### Success Criteria:

#### Automated Verification:
- [ ] All integration tests pass: `bin/gradle test`
- [ ] Full build succeeds: `bin/gradle build`
- [ ] API dump succeeds: `bin/gradle apiDump`
- [ ] API check passes: `bin/gradle apiCheck`

#### Manual Verification:
- [ ] Events flow correctly through Docker relay
- [ ] Both NIP-04 and NIP-44 work end-to-end
- [ ] Client can decrypt service responses
- [ ] Service can decrypt client requests
- [ ] Notifications are received correctly

**Implementation Note**: After completing this phase and all automated verification passes, confirm all manual tests pass before considering implementation complete.

---

## Testing Strategy

### Unit Tests:
- Tag serialization/deserialization
- URI parsing with valid/invalid inputs
- Event model creation and signing
- Encryption/decryption with both NIP-04 and NIP-44
- JSON serialization of request/response/notification payloads
- Error handling for invalid encryption methods

### Integration Tests:
- Complete request-response flow through relay
- Notification delivery
- Dual encryption method support
- Invalid signature rejection
- Connection establishment via URI

### Manual Testing Steps:
1. Parse various `nostr+walletconnect://` URIs with different parameters
2. Create and sign all event types (13194, 23194, 23195, 23197)
3. Verify encrypted content is not readable in plaintext
4. Test both NIP-04 and NIP-44 encryption paths
5. Confirm events properly filter by tags in relay queries
6. Verify error responses serialize correctly

## Performance Considerations

- NIP-44 encryption is more secure but slightly slower than NIP-04
- Message keys are properly cleared from memory after use ([Nip44.kt:128](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44.kt#L128))
- Lazy adapter initialization prevents unnecessary Moshi overhead
- Connection key derivation is cached per conversation (ECDH shared secret)

## Migration Notes

N/A - This is a new feature addition with no breaking changes to existing APIs.

## References

- Original research: [history/NIP-47_Implementation_Research.md](file:///Users/jmawson/Development/sourcery/nostrino/history/NIP-47_Implementation_Research.md)
- NIP-47 spec: https://github.com/nostr-protocol/nips/blob/master/47.md
- NIP-44 spec: https://github.com/nostr-protocol/nips/blob/master/44.md
- NIP-04 spec: https://github.com/nostr-protocol/nips/blob/master/04.md
- Existing NIP-44 implementation: [Nip44.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/crypto/Nip44.kt)
- ZapRequest pattern: [ZapRequest.kt](file:///Users/jmawson/Development/sourcery/nostrino/lib/src/jvmMain/kotlin/app/cash/nostrino/model/ZapRequest.kt)
