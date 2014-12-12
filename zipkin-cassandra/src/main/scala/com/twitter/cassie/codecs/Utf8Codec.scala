// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.codecs

import java.nio.ByteBuffer
import org.apache.thrift.bootleg.Utf8Helper

/**
 * Encodes and decodes values as UTF-8 strings.
 */
object Utf8Codec extends Codec[String] {
  def encode(s: String) = b2b(s.getBytes("UTF-8"))
  def decode(ary: ByteBuffer) = new String(ary.array, ary.position, ary.remaining, "UTF-8")
}
