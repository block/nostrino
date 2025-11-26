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

package app.cash.nostrino.client

import app.cash.nostrino.client.ConnectionState.Connected
import app.cash.nostrino.client.ConnectionState.Disconnected
import app.cash.nostrino.client.ConnectionState.Disconnecting
import app.cash.nostrino.client.ConnectionState.Failing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.runBlocking
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import app.cash.nostrino.message.relay.RelayMessage
import mu.KotlinLogging
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RelayListener(
  private val label: String,
  private val connectionStateListener: ConnectionStateListener
) : WebSocketListener() {

  private val messages = MutableSharedFlow<RelayMessage>(replay = 1024)

  fun messages(): Flow<RelayMessage> = messages.asSharedFlow().buffer()

  private val relayMessageAdapter = moshi.adapter(RelayMessage::class.java)

  override fun onOpen(webSocket: WebSocket, response: Response) {
    logger.info { "Socket is open. [relay=$label][response=${response.message}]" }
    connectionStateListener.update(Connected)
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    logger.info { "Received $text. [relay=$label]" }
    runBlocking {
      relayMessageAdapter.fromJson(text)?.let { messages.emit(it) }
        ?: logger.warn { "Unable to handle relay message: $text. [relay=$label]" }
    }
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    logger.info(t) { "WebSocket failure. [relay=$label]" }
    connectionStateListener.update(Failing)
  }

  override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    logger.info { "Socket is closed. [relay=$label]" }
    connectionStateListener.update(Disconnected)
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    logger.info { "Socket is closing. [relay=$label]" }
    connectionStateListener.update(Disconnecting)
  }

  companion object {
    val logger = KotlinLogging.logger {}
  }
}
