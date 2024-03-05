/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;

public class Proto3SpanWriterTest {
  Proto3SpanWriter writer = new Proto3SpanWriter();

  /** proto messages always need a key, so the non-list form is just a single-field */
  @Test void write_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = writer.write(CLIENT_SPAN);

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN))
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test void writeList_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = writer.writeList(List.of(CLIENT_SPAN));

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN))
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test void writeList_multiple() {
    byte[] bytes = writer.writeList(List.of(CLIENT_SPAN, CLIENT_SPAN));

    assertThat(bytes)
      .hasSize(writer.sizeInBytes(CLIENT_SPAN) * 2)
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }

  @Test void writeList_empty() {
    assertThat(writer.writeList(List.of()))
      .isEmpty();
  }

  @Test void writeList_offset_startsWithSpanKeyAndLengthPrefix() {
    byte[] bytes = new byte[2048];
    writer.writeList(List.of(CLIENT_SPAN, CLIENT_SPAN), bytes, 0);

    assertThat(bytes)
      .startsWith((byte) 10, SPAN.sizeOfValue(CLIENT_SPAN));
  }
}
