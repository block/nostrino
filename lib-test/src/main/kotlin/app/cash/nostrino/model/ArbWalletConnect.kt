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

import app.cash.nostrino.ArbPrimitive.arbVanillaString
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull

object ArbWalletConnect {

  val arbWalletError: Arb<WalletError> = Arb.bind(
    Arb.element(
      "RATE_LIMITED", "NOT_IMPLEMENTED", "INSUFFICIENT_BALANCE",
      "QUOTA_EXCEEDED", "RESTRICTED", "UNAUTHORIZED", "INTERNAL",
      "UNSUPPORTED_ENCRYPTION", "OTHER", "PAYMENT_FAILED", "NOT_FOUND"
    ),
    arbVanillaString
  ) { code, message ->
    WalletError(code, message)
  }

  val arbPayInvoiceParams: Arb<WalletRequestParams.PayInvoice> = Arb.bind(
    arbVanillaString,
    Arb.long(min = 0L, max = 1_000_000_000L).orNull()
  ) { invoice, amount ->
    WalletRequestParams.PayInvoice(invoice, amount)
  }

  val arbMakeInvoiceParams: Arb<WalletRequestParams.MakeInvoice> = Arb.bind(
    Arb.long(min = 0L, max = 1_000_000_000L),
    arbVanillaString.orNull(),
    arbVanillaString.orNull(),
    Arb.int(min = 60, max = 86400).orNull()
  ) { amount, description, descriptionHash, expiry ->
    WalletRequestParams.MakeInvoice(amount, description, descriptionHash, expiry)
  }

  val arbGetBalanceParams: Arb<WalletRequestParams.GetBalance> =
    Arb.bind { WalletRequestParams.GetBalance() }

  val arbGetInfoParams: Arb<WalletRequestParams.GetInfo> =
    Arb.bind { WalletRequestParams.GetInfo() }

  val arbWalletRequestParams: Arb<WalletRequestParams> = Arb.choice(
    arbPayInvoiceParams,
    arbMakeInvoiceParams,
    arbGetBalanceParams,
    arbGetInfoParams
  )

  val arbPayInvoiceResult: Arb<WalletResponseResult.PayInvoice> = Arb.bind(
    arbVanillaString,
    Arb.long(min = 0L, max = 100_000L).orNull()
  ) { preimage, feesPaid ->
    WalletResponseResult.PayInvoice(preimage, feesPaid)
  }

  val arbMakeInvoiceResult: Arb<WalletResponseResult.MakeInvoice> = Arb.bind(
    arbVanillaString,
    arbVanillaString,
    Arb.long(min = 0L, max = 1_000_000_000L),
    Arb.long(min = 1_600_000_000L, max = 2_000_000_000L)
  ) { invoice, paymentHash, amount, createdAt ->
    WalletResponseResult.MakeInvoice(
      type = "incoming",
      invoice = invoice,
      paymentHash = paymentHash,
      amount = amount,
      createdAt = createdAt
    )
  }

  val arbGetBalanceResult: Arb<WalletResponseResult.GetBalance> =
    Arb.long(min = 0L, max = 100_000_000_000L).map { balance ->
      WalletResponseResult.GetBalance(balance)
    }

  val arbGetInfoResult: Arb<WalletResponseResult.GetInfo> = Arb.bind(
    arbVanillaString,
    arbVanillaString,
    Arb.element("mainnet", "testnet", "signet", "regtest"),
    Arb.list(Arb.element("pay_invoice", "make_invoice", "get_balance", "get_info"), 1..4)
  ) { alias, pubkey, network, methodList ->
    WalletResponseResult.GetInfo(
      alias = alias,
      pubkey = pubkey,
      network = network,
      methods = methodList
    )
  }

  val arbWalletResponseResult: Arb<WalletResponseResult> = Arb.choice(
    arbPayInvoiceResult,
    arbMakeInvoiceResult,
    arbGetBalanceResult,
    arbGetInfoResult
  )

  val arbPaymentReceivedNotification: Arb<WalletNotificationData.PaymentReceived> = Arb.bind(
    arbVanillaString,
    arbVanillaString,
    Arb.long(min = 0L, max = 1_000_000_000L),
    Arb.long(min = 1_600_000_000L, max = 2_000_000_000L),
    Arb.long(min = 1_600_000_000L, max = 2_000_000_000L)
  ) { paymentHash, preimage, amount, createdAt, settledAt ->
    WalletNotificationData.PaymentReceived(
      type = "incoming",
      state = "settled",
      paymentHash = paymentHash,
      preimage = preimage,
      amount = amount,
      createdAt = createdAt,
      settledAt = settledAt
    )
  }

  val arbPaymentSentNotification: Arb<WalletNotificationData.PaymentSent> = Arb.bind(
    arbVanillaString,
    arbVanillaString,
    Arb.long(min = 0L, max = 1_000_000_000L),
    Arb.long(min = 0L, max = 100_000L),
    Arb.long(min = 1_600_000_000L, max = 2_000_000_000L),
    Arb.long(min = 1_600_000_000L, max = 2_000_000_000L)
  ) { paymentHash, preimage, amount, feesPaid, createdAt, settledAt ->
    WalletNotificationData.PaymentSent(
      type = "outgoing",
      state = "settled",
      paymentHash = paymentHash,
      preimage = preimage,
      amount = amount,
      feesPaid = feesPaid,
      createdAt = createdAt,
      settledAt = settledAt
    )
  }

  val arbWalletNotificationData: Arb<WalletNotificationData> = Arb.choice(
    arbPaymentReceivedNotification,
    arbPaymentSentNotification
  )
}
