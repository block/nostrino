/*
 * Copyright (c) 2025 Block, Inc.
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
 */

package app.cash.nostrino.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Error structure for NIP-47 wallet responses.
 */
@JsonClass(generateAdapter = true)
data class WalletError(
  val code: String,
  val message: String,
)

/**
 * Request parameters for NIP-47 wallet commands.
 */
sealed interface WalletRequestParams {
  @JsonClass(generateAdapter = true)
  data class PayInvoice(
    val invoice: String,
    val amount: Long? = null,
  ) : WalletRequestParams

  @JsonClass(generateAdapter = true)
  data class MakeInvoice(
    val amount: Long,
    val description: String? = null,
    @Json(name = "description_hash") val descriptionHash: String? = null,
    val expiry: Int? = null,
  ) : WalletRequestParams

  @JsonClass(generateAdapter = true)
  data class LookupInvoice(
    @Json(name = "payment_hash") val paymentHash: String? = null,
    val invoice: String? = null,
  ) : WalletRequestParams

  @JsonClass(generateAdapter = true)
  data class GetBalance(val dummy: Unit? = null) : WalletRequestParams

  @JsonClass(generateAdapter = true)
  data class GetInfo(val dummy: Unit? = null) : WalletRequestParams
}

/**
 * Result data for NIP-47 wallet responses.
 */
sealed interface WalletResponseResult {
  @JsonClass(generateAdapter = true)
  data class PayInvoice(
    val preimage: String,
    @Json(name = "fees_paid") val feesPaid: Long? = null,
  ) : WalletResponseResult

  @JsonClass(generateAdapter = true)
  data class MakeInvoice(
    val type: String,
    val invoice: String,
    @Json(name = "payment_hash") val paymentHash: String,
    val amount: Long,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    val metadata: Map<String, String>? = null,
  ) : WalletResponseResult

  @JsonClass(generateAdapter = true)
  data class GetBalance(
    val balance: Long,
  ) : WalletResponseResult

  @JsonClass(generateAdapter = true)
  data class GetInfo(
    val alias: String,
    val color: String? = null,
    val pubkey: String,
    val network: String,
    @Json(name = "block_height") val blockHeight: Int? = null,
    @Json(name = "block_hash") val blockHash: String? = null,
    val methods: List<String>,
  ) : WalletResponseResult
}

/**
 * Notification data for NIP-47 wallet events.
 */
sealed interface WalletNotificationData {
  @JsonClass(generateAdapter = true)
  data class PaymentReceived(
    val type: String,
    val state: String? = null,
    val invoice: String? = null,
    val description: String? = null,
    @Json(name = "description_hash") val descriptionHash: String? = null,
    val preimage: String,
    @Json(name = "payment_hash") val paymentHash: String,
    val amount: Long,
    @Json(name = "fees_paid") val feesPaid: Long? = null,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    @Json(name = "settled_at") val settledAt: Long? = null,
  ) : WalletNotificationData

  @JsonClass(generateAdapter = true)
  data class PaymentSent(
    val type: String,
    val state: String? = null,
    val invoice: String? = null,
    val description: String? = null,
    @Json(name = "description_hash") val descriptionHash: String? = null,
    val preimage: String,
    @Json(name = "payment_hash") val paymentHash: String,
    val amount: Long,
    @Json(name = "fees_paid") val feesPaid: Long,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    @Json(name = "settled_at") val settledAt: Long? = null,
  ) : WalletNotificationData
}
