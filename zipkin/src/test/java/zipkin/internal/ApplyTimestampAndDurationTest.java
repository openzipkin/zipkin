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
import zipkin.Annotation;
import zipkin.Endpoint;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;

public class ApplyTimestampAndDurationTest {

  Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2, 8080);
  Annotation cs = Annotation.create((50) * 1000, CLIENT_SEND, frontend);

  Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 2, 8080);
  Annotation sr = Annotation.create((95) * 1000, SERVER_RECV, backend);

  Span.Builder span = Span.builder().traceId(1).name("method1").id(666);

  @Test
  public void bestTimestamp_isSpanTimestamp() {
    assertThat(guessTimestamp(span.timestamp(1L).build()))
        .isEqualTo(1L);
  }

  @Test
  public void bestTimestamp_isNotARandomAnnotation() {
    assertThat(guessTimestamp(span.addAnnotation(Annotation.create(1L, "foo", frontend)).build()))
        .isNull();
  }

  @Test
  public void bestTimestamp_isARootServerSpan() {
    assertThat(guessTimestamp(span.addAnnotation(sr).build()))
        .isEqualTo(sr.timestamp);
  }

  @Test
  public void bestTimestamp_isClientSideOFARootSpan() {
    assertThat(guessTimestamp(span.addAnnotation(cs).addAnnotation(sr).build()))
        .isEqualTo(cs.timestamp);
  }

  @Test
  public void bestTimestamp_isNotAChildServerSpan() {
    assertThat(guessTimestamp(span.parentId(2L).addAnnotation(sr).build()))
        .isNull();
  }

  @Test
  public void bestTimestamp_isAChildClientSpan() {
    assertThat(guessTimestamp(span.parentId(2L).addAnnotation(cs).build()))
        .isEqualTo(cs.timestamp);
  }
}
