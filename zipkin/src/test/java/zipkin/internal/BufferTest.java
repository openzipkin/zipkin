/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.internal;

import org.junit.Test;

public class BufferTest {

  // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
  @Test public void testUtf8Len() {
    for (int codepoint = 0; codepoint <= 0x10FFFF; codepoint++) {
      if (codepoint == 0xD800) codepoint = 0xDFFF + 1; // skip surrogates
      if (Character.isDefined(codepoint)) {
        String test = new String(Character.toChars(codepoint));
        int expected = test.getBytes(Util.UTF_8).length;
        int actual = Buffer.utf8SizeInBytes(test);
        if (actual != expected) {
          throw new AssertionError(actual + " length != " + expected + " for " + codepoint);
        }
      }
    }
  }
}
