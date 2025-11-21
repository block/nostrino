/*
 * Copyright (c) 2023 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package app.cash.nostrino.model

import app.cash.nostrino.crypto.PubKey
import okio.ByteString
import okio.ByteString.Companion.decodeHex

sealed interface Tag {
  fun toJsonList(): List<String>

  companion object {
    fun parseRaw(strings: List<String>): Tag {
      require(strings.size >= 2) { "Invalid tag format: $strings" }
      val (tag, value) = strings
      val values = strings.drop(1)
      return when (tag) {
        "e" -> EventTag(value.decodeHex())
        "p" -> PubKeyTag(PubKey(value.decodeHex()))
        "t" -> HashTag(value)
        "amount" -> AmountTag(value.toLong())
        "lnurl" -> LnUrlTag(value)
        "relays" -> RelaysTag(values)
        "bolt11" -> Bolt11Tag(value)
        "preimage" -> PreimageTag(value.decodeHex())
        "description" -> {
          val event = Event.fromJson(value)
          require(event != null) { "Invalid tag format: $strings" }

          ZapReceiptDescriptionTag(event)
        }

        "encryption" -> EncryptionTag(value)
        "notifications" -> NotificationsTag(value)
        else -> throw IllegalArgumentException("Invalid tag format: $strings")
      }
    }
  }
}

data class EventTag(val eventId: ByteString) : Tag {
  override fun toJsonList() = listOf("e", eventId.hex())
}

data class PubKeyTag(val pubKey: PubKey) : Tag {
  override fun toJsonList() = listOf("p", pubKey.hex())
}

data class HashTag(val label: String) : Tag {
  override fun toJsonList() = listOf("t", label)
}

data class RelaysTag(val relays: List<String>) : Tag {
  override fun toJsonList() = listOf("relays") + relays
}

data class AmountTag(val amount: Long) : Tag {
  override fun toJsonList() = listOf("amount", amount.toString())
}

data class LnUrlTag(val lnurl: String) : Tag {
  override fun toJsonList() = listOf("lnurl", lnurl)
}

data class Bolt11Tag(val bolt11: String) : Tag {
  override fun toJsonList() = listOf("bolt11", bolt11)
}

data class PreimageTag(val preimage: ByteString) : Tag {
  override fun toJsonList() = listOf("preimage", preimage.hex())
}

data class ZapReceiptDescriptionTag(val description: Event) : Tag {
  override fun toJsonList(): List<String> = listOf(
    "description", description.toJson(),
  )
}

data class EncryptionTag(val encryptionSchemes: String) : Tag {
  override fun toJsonList() = listOf("encryption", encryptionSchemes)
}

data class NotificationsTag(val notificationTypes: String) : Tag {
  override fun toJsonList() = listOf("notifications", notificationTypes)
}
