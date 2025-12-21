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

import app.cash.nostrino.model.ArbWalletConnect.arbGetBalanceParams
import app.cash.nostrino.model.ArbWalletConnect.arbGetBalanceResult
import app.cash.nostrino.model.ArbWalletConnect.arbGetInfoParams
import app.cash.nostrino.model.ArbWalletConnect.arbGetInfoResult
import app.cash.nostrino.model.ArbWalletConnect.arbMakeInvoiceParams
import app.cash.nostrino.model.ArbWalletConnect.arbMakeInvoiceResult
import app.cash.nostrino.model.ArbWalletConnect.arbPayInvoiceParams
import app.cash.nostrino.model.ArbWalletConnect.arbPayInvoiceResult
import app.cash.nostrino.model.ArbWalletConnect.arbPaymentReceivedNotification
import app.cash.nostrino.model.ArbWalletConnect.arbPaymentSentNotification
import app.cash.nostrino.model.ArbWalletConnect.arbWalletError
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class WalletConnectModelsTest : StringSpec({

  "WalletError serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletError::class.java)
    checkAll(arbWalletError) { error ->
      val json = adapter.toJson(error)
      adapter.fromJson(json) shouldBe error
    }
  }

  "PayInvoice params serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletRequestParams.PayInvoice::class.java)
    checkAll(arbPayInvoiceParams) { params ->
      val json = adapter.toJson(params)
      adapter.fromJson(json) shouldBe params
    }
  }

  "MakeInvoice params serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletRequestParams.MakeInvoice::class.java)
    checkAll(arbMakeInvoiceParams) { params ->
      val json = adapter.toJson(params)
      adapter.fromJson(json) shouldBe params
    }
  }

  "GetBalance params serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletRequestParams.GetBalance::class.java)
    checkAll(arbGetBalanceParams) { params ->
      val json = adapter.toJson(params)
      adapter.fromJson(json) shouldBe params
    }
  }

  "GetInfo params serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletRequestParams.GetInfo::class.java)
    checkAll(arbGetInfoParams) { params ->
      val json = adapter.toJson(params)
      adapter.fromJson(json) shouldBe params
    }
  }

  "PayInvoice result serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletResponseResult.PayInvoice::class.java)
    checkAll(arbPayInvoiceResult) { result ->
      val json = adapter.toJson(result)
      adapter.fromJson(json) shouldBe result
    }
  }

  "MakeInvoice result serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletResponseResult.MakeInvoice::class.java)
    checkAll(arbMakeInvoiceResult) { result ->
      val json = adapter.toJson(result)
      adapter.fromJson(json) shouldBe result
    }
  }

  "GetBalance result serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletResponseResult.GetBalance::class.java)
    checkAll(arbGetBalanceResult) { result ->
      val json = adapter.toJson(result)
      adapter.fromJson(json) shouldBe result
    }
  }

  "GetInfo result serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletResponseResult.GetInfo::class.java)
    checkAll(arbGetInfoResult) { result ->
      val json = adapter.toJson(result)
      adapter.fromJson(json) shouldBe result
    }
  }

  "PaymentReceived notification serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletNotificationData.PaymentReceived::class.java)
    checkAll(arbPaymentReceivedNotification) { notification ->
      val json = adapter.toJson(notification)
      adapter.fromJson(json) shouldBe notification
    }
  }

  "PaymentSent notification serializes and deserializes correctly" {
    val adapter = moshi.adapter(WalletNotificationData.PaymentSent::class.java)
    checkAll(arbPaymentSentNotification) { notification ->
      val json = adapter.toJson(notification)
      adapter.fromJson(json) shouldBe notification
    }
  }
})
