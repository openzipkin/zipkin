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
package zipkin2;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import okio.Buffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin2.TestObjects.FRONTEND;

public class SpanTest {
  Span base = Span.newBuilder().traceId("1").id("1").localEndpoint(FRONTEND).build();

  @Test public void traceIdString() {
    Span with128BitId = base.toBuilder()
      .traceId("463ac35c9f6413ad48485a3953bb6124")
      .name("foo").build();

    assertThat(with128BitId.traceId())
      .isEqualTo("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test public void spanNamesLowercase() {
    assertThat(base.toBuilder().name("GET").build().name())
      .isEqualTo("get");
  }

  @Test public void annotationsSortByTimestamp() {
    Span span = base.toBuilder()
      .addAnnotation(2L, "foo")
      .addAnnotation(1L, "foo")
      .build();

    // note: annotations don't also have endpoints, as it is implicit to Span.localEndpoint
    assertThat(span.annotations()).containsExactly(
      Annotation.create(1L, "foo"),
      Annotation.create(2L, "foo")
    );
  }

  @Test public void putTagOverwritesValue() {
    Span span = base.toBuilder()
      .putTag("foo", "bar")
      .putTag("foo", "qux")
      .build();

    assertThat(span.tags()).containsExactly(
      entry("foo", "qux")
    );
  }

  @Test public void builder_canUnsetParent() {
    Span withParent = base.toBuilder().parentId("3").build();

    assertThat(withParent.toBuilder().parentId(null).build().parentId())
      .isNull();
  }

  @Test public void clone_differentCollections() {
    Span.Builder builder = base.toBuilder()
      .addAnnotation(1L, "foo")
      .putTag("foo", "qux");

    Span.Builder builder2 = builder.clone()
      .addAnnotation(2L, "foo")
      .putTag("foo", "bar");

    assertThat(builder.build()).isEqualTo(base.toBuilder()
      .addAnnotation(1L, "foo")
      .putTag("foo", "qux")
      .build()
    );

    assertThat(builder2.build()).isEqualTo(base.toBuilder()
      .addAnnotation(1L, "foo")
      .addAnnotation(2L, "foo")
      .putTag("foo", "bar")
      .build()
    );
  }

  /** Catches common error when zero is passed instead of null for a timestamp */
  @Test public void coercesZeroTimestampsToNull() {
    Span span = base.toBuilder()
      .timestamp(0L)
      .duration(0L)
      .build();

    assertThat(span.timestamp())
      .isNull();
    assertThat(span.duration())
      .isNull();
  }

  @Test public void canUsePrimitiveOverloads() {
    Span primitives = base.toBuilder()
      .timestamp(1L)
      .duration(1L)
      .shared(true)
      .debug(true)
      .build();

    Span objects =  base.toBuilder()
      .timestamp(Long.valueOf(1L))
      .duration(Long.valueOf(1L))
      .shared(Boolean.TRUE)
      .debug(Boolean.TRUE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
  }

  @Test public void nullToZeroOrFalse() {
    Span nulls = base.toBuilder()
      .timestamp(null)
      .duration(null)
      .build();

    Span zeros =  base.toBuilder()
      .timestamp(0L)
      .duration(0L)
      .build();

    assertThat(nulls)
      .isEqualToComparingFieldByField(zeros);
  }

  @Test public void toString_isJson() {
    assertThat(base.toString()).hasToString(
      "{\"traceId\":\"0000000000000001\",\"id\":\"0000000000000001\",\"localEndpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}"
    );
  }

  /** Test serializable as used in spark jobs. Careful to include all non-standard fields */
  @Test public void serialization() throws Exception {
    Buffer buffer = new Buffer();

    Span span = base.toBuilder()
      .addAnnotation(1L, "foo")
      .build();

    new ObjectOutputStream(buffer.outputStream()).writeObject(span);

    assertThat(new ObjectInputStream(buffer.inputStream()).readObject())
      .isEqualTo(span);
  }
}
