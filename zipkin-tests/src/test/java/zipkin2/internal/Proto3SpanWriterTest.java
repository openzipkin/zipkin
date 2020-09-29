/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;

public class Proto3SpanWriterTest {
  Proto3SpanWriter writer = new Proto3SpanWriter();

  /** proto messages always need a key, so the non-list form is just a single-field */
  @Test public void write_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = writer.write(CLIENT_SPAN);

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN))
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test public void writeList_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = writer.writeList(asList(CLIENT_SPAN));

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN))
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test public void writeList_multiple() {
    byte[] bytes = writer.writeList(asList(CLIENT_SPAN, CLIENT_SPAN));

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN) * 2)
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test public void writeList_empty() {
    assertThat(writer.writeList(asList()))
      .isEmpty();
  }

  @Test public void writeList_offset_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = new byte[2048];
    writer.writeList(asList(CLIENT_SPAN, CLIENT_SPAN), bytes, 0);

    assertThat(bytes)
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }
}
