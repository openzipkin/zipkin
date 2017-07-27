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
package zipkin.internal;

import org.junit.Test;
import zipkin.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin.TestObjects.APP_ENDPOINT;

public class Span2Test {
  Span2 base = Span2.builder().traceId(1L).id(1L).localEndpoint(APP_ENDPOINT).build();

  @Test public void traceIdString() {
    Span2 with128BitId = Span2.builder()
      .traceId(Util.lowerHexToUnsignedLong("48485a3953bb6124"))
      .id(1)
      .name("foo").build();

    assertThat(with128BitId.traceIdString())
      .isEqualTo("48485a3953bb6124");
  }

  @Test public void traceIdString_high() {
    Span2 with128BitId = Span2.builder()
      .traceId(Util.lowerHexToUnsignedLong("48485a3953bb6124"))
      .traceIdHigh(Util.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .id(1)
      .name("foo").build();

    assertThat(with128BitId.traceIdString())
      .isEqualTo("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test public void spanNamesLowercase() {
    assertThat(base.toBuilder().name("GET").build().name())
      .isEqualTo("get");
  }

  @Test public void annotationsSortByTimestamp() {
    Span2 span = base.toBuilder()
      .addAnnotation(2L, "foo")
      .addAnnotation(1L, "foo")
      .build();

    // note: annotations don't also have endpoints, as it is implicit to Span2.localEndpoint
    assertThat(span.annotations()).containsExactly(
      Annotation.create(1L, "foo", null),
      Annotation.create(2L, "foo", null)
    );
  }

  @Test public void putTagOverwritesValue() {
    Span2 span = base.toBuilder()
      .putTag("foo", "bar")
      .putTag("foo", "qux")
      .build();

    assertThat(span.tags()).containsExactly(
      entry("foo", "qux")
    );
  }

  @Test public void clone_differentCollections() {
    Span2.Builder builder = base.toBuilder()
      .addAnnotation(1L, "foo")
      .putTag("foo", "qux");

    Span2.Builder builder2 = builder.clone()
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
    Span2 span = base.toBuilder()
      .timestamp(0L)
      .duration(0L)
      .build();

    assertThat(span.timestamp())
      .isNull();
    assertThat(span.duration())
      .isNull();
  }

  // TODO: toString_isJson

  // TODO: serialization

  // TODO: serializationUsesJson
}
