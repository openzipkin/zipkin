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
import java.lang.String
import org.apache.thrift.bootleg.Utf8Helper

/**
 * Encodes and decodes values as UTF-8 strings.
 */
@deprecated("""Use the new Utf8Codec if you can. You may need to use this for backwards
  compatability with your stored data. This should only be a problem if you
  use codepoints outside the BMP.""", "0.15.0")
object LegacyUtf8Codec extends Codec[String] {
  @deprecated("""Use the new Utf8Codec if you can. You may need to use this for backwards
    compatability with your stored data. This should only be a problem if you
    use codepoints outside the BMP.""", "0.15.0")
  def encode(s: String) = b2b(Utf8Helper.encode(s))
  @deprecated("""Use the new Utf8Codec if you can. You may need to use this for backwards
    compatability with your stored data. This should only be a problem if you
    use codepoints outside the BMP.""", "0.15.0")
  def decode(ary: ByteBuffer) = Utf8Helper.decode(b2b(ary))
}
