/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin2.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static zipkin2.TestObjects.UTF_8;

public class JsonCodecTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void doesntStackOverflowOnToBufferWriterBug_lessThanBytes() {
    thrown.expect(AssertionError.class);
    thrown.expectMessage("Bug found using FooWriter to write Foo as json. Wrote 1/2 bytes: a");

    class FooWriter implements Buffer.Writer {
      @Override public int sizeInBytes(Object value) {
        return 2;
      }

      @Override public void write(Object value, Buffer buffer) {
        buffer.writeByte('a');
        throw new RuntimeException("buggy");
      }
    }

    class Foo {
      @Override public String toString() {
        return new String(JsonCodec.write(new FooWriter(), this), UTF_8);
      }
    }

    new Foo().toString();
  }

  @Test public void doesntStackOverflowOnToBufferWriterBug_Overflow() {
    thrown.expect(AssertionError.class);
    thrown.expectMessage("Bug found using FooWriter to write Foo as json. Wrote 2/2 bytes: ab");

    // pretend there was a bug calculating size, ex it calculated incorrectly as to small
    class FooWriter implements Buffer.Writer {
      @Override public int sizeInBytes(Object value) {
        return 2;
      }

      @Override public void write(Object value, Buffer buffer) {
        buffer.writeByte('a').writeByte('b').writeByte('c'); // wrote larger than size!
      }
    }

    class Foo {
      @Override public String toString() {
        return new String(JsonCodec.write(new FooWriter(), this), UTF_8);
      }
    }

    new Foo().toString();
  }
}
