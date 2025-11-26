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

package app.cash.nostrino.protocol.serde

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import java.time.Instant

class PrimitiveAdapter {

  @FromJson
  fun byteStringFromJson(s: String): ByteString = s.decodeHex()

  @ToJson
  fun byteStringToJson(b: ByteString): String = b.hex()

  @FromJson
  fun instantFromJson(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

  @ToJson
  fun instantToJson(i: Instant): Long = i.epochSecond
}
