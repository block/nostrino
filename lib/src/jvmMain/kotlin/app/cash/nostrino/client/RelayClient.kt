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
import app.cash.nostrino.client.ConnectionState.Connecting
import app.cash.nostrino.client.ConnectionState.Disconnected
import app.cash.nostrino.client.ConnectionState.Disconnecting
import app.cash.nostrino.client.ConnectionState.Failing
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import app.cash.nostrino.protocol.serde.ClientMessageType
import app.cash.nostrino.protocol.serde.NostrJson.moshi
import app.cash.nostrino.message.relay.EventMessage
import app.cash.nostrino.message.relay.RelayMessage
import app.cash.nostrino.model.Event
import app.cash.nostrino.model.Filter
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Provides comms to and from a single relay */
class RelayClient(
  private val url: String,
  private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
) : Relay(), ConnectionStateListener {

  private val jsonListAdapter = moshi.adapter(List::class.java)
  private val queuedMessages = MutableSharedFlow<String>(replay = 512)
  private val listener = RelayListener(url, this)
  private val subscriptions: MutableMap<Subscription, Set<Filter>> = Collections.synchronizedMap(mutableMapOf())

  private var _connectionState = MutableStateFlow(Disconnected)
  val connectionState : StateFlow<ConnectionState> get() = _connectionState.asStateFlow() // TODO add tests

  private var messageSendingJob: Job? = null
  private var socket: WebSocket? = null

  override val relayMessages: Flow<RelayMessage> by lazy { listener.messages() }

  override fun start() {
    if (connectionState.value == Disconnected) {
      logger.info { "Connecting to $url" }
      socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
      _connectionState.value = Connecting
    }
  }

  override fun update(newState: ConnectionState): Unit = runBlocking {
    logger.info { "$connectionState -> $newState [relay=$url]" }
    when (newState) {
      Connecting -> {}
      Connected -> startTalking()
      Failing -> restart()
      Disconnected -> connect()
      Disconnecting -> stopTalking()
    }

    _connectionState.value = newState
  }

  override fun stop() {
    stopTalking()
    disconnect()
  }

  override fun send(event: Event) {
    logger.info { "Enqueuing: ${event.id} [relay=$url]" }
    send(listOf(ClientMessageType.EVENT.name, event))
  }

  override fun subscribe(filters: Set<Filter>, subscription: Subscription): Subscription = subscription.also {
    send(listOf<Any>(ClientMessageType.REQ.name, it.id).plus(filters.toList()))
    subscriptions[subscription] = filters
  }

  override fun unsubscribe(subscription: Subscription) {
    subscriptions.remove(subscription)
    send(listOf(ClientMessageType.CLOSE.name, subscription.id))
  }

  override val allEvents: Flow<Event> by lazy { relayMessages.filterIsInstance<EventMessage>().map { it.event } }

  private fun send(message: List<Any>) {
    queuedMessages.tryEmit(jsonListAdapter.toJson(message))
  }

  private suspend fun connect(after: Long = 0L) = coroutineScope {
    delay(after)
    logger.info { "Connecting to $url" }
    socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
  }

  private suspend fun startTalking() = coroutineScope {
    messageSendingJob = CoroutineScope(Dispatchers.IO + CoroutineName(url)).launch {
      logger.info { "Starting send loop [relay=$url]" }
      queuedMessages.collect {
        logger.info { "Sending [relay=$url] $it" }
        if (socket?.send(it) != true) {
          logger.warn { "Failed to send on socket. Re-enqueueing & restarting. [relay=$url]" }
          queuedMessages.emit(it)
          restart()
        }
      }
    }
  }

  private fun restart() {
    stop()
    start()
    subscriptions.forEach { (sub, filters) ->
      subscribe(filters, sub)
    }
  }

  private fun stopTalking() {
    logger.info { "Stopping the send loop. [relay=$url]" }
    messageSendingJob?.cancel()
    messageSendingJob = null
  }

  private fun disconnect() {
    logger.info { "Disconnecting from $url" }
    if (socket?.close(1000, "Requested disconnection") == false) {
      logger.warn { "Unable to close socket cleanly. [relay=$url]" }
      socket?.cancel()
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}
  }
}

enum class ConnectionState {
  Disconnected,
  Connecting,
  Connected,
  Failing,
  Disconnecting
}
