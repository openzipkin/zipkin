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
import zipkin.Endpoint;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.internal.ApplyTimestampAndDuration.apply;
import static zipkin.internal.ApplyTimestampAndDuration.authoritativeTimestamp;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;

public class ApplyTimestampAndDurationTest {

  Endpoint frontend =
      Endpoint.builder().serviceName("frontend").ipv4(192 << 24 | 12 << 16 | 1).port(8080).build();
  Annotation cs = Annotation.create((50) * 1000, CLIENT_SEND, frontend);
  Annotation cr = Annotation.create((100) * 1000, CLIENT_RECV, frontend);

  Endpoint backend =
      Endpoint.builder().serviceName("backend").ipv4(192 << 24 | 12 << 16 | 2).port(8080).build();
  Annotation sr = Annotation.create((70) * 1000, SERVER_RECV, backend);
  Annotation ss = Annotation.create((80) * 1000, SERVER_SEND, backend);

  Span.Builder span = Span.builder().traceId(1).name("method1").id(666);

  @Test
  public void apply_onlyCs() {
    assertThat(apply(span.addAnnotation(cs).build()).timestamp)
        .isEqualTo(cs.timestamp);
  }

  @Test
  public void apply_rpcSpan() {
    assertThat(apply(span
        .addAnnotation(cs)
        .addAnnotation(sr)
        .addAnnotation(ss)
        .addAnnotation(cr).build()).duration)
        .isEqualTo(cr.timestamp - cs.timestamp);
  }

  @Test
  public void apply_serverOnly() {
    assertThat(apply(span.addAnnotation(sr).addAnnotation(ss).build()).duration)
        .isEqualTo(ss.timestamp - sr.timestamp);
  }

  @Test
  public void apply_oneWay() {
    assertThat(apply(span.addAnnotation(cs).addAnnotation(sr).build()).duration)
        .isEqualTo(sr.timestamp - cs.timestamp);
  }

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
  public void bestTimestamp_isClientSideOfASharedSpan() {
    assertThat(guessTimestamp(span.addAnnotation(cs).addAnnotation(sr).build()))
        .isEqualTo(cs.timestamp);
  }

  @Test
  public void bestTimestamp_serverSideOfChildSpan() {
    assertThat(guessTimestamp(span.parentId(2L).addAnnotation(sr).build()))
        .isEqualTo(sr.timestamp);
  }

  @Test
  public void bestTimestamp_isClientSideOfAChildSpan() {
    assertThat(guessTimestamp(span.parentId(2L).addAnnotation(sr).addAnnotation(cs).build()))
        .isEqualTo(cs.timestamp);
  }

  @Test
  public void bestTimestamp_isNotRandomAnnotation() {
    assertThat(guessTimestamp(span.addAnnotation(sr.toBuilder().value("f").build()).build()))
        .isNull();
  }

  @Test
  public void authoritativeTimestamp_isTimestamp() {
    assertThat(authoritativeTimestamp(span.parentId(2L).timestamp(1L).addAnnotation(cs).build()))
        .isEqualTo(1L);
  }

  @Test
  public void authoritativeTimestamp_isClientSideOfAChildSpan() {
    assertThat(authoritativeTimestamp(span.parentId(2L).addAnnotation(cs).build()))
        .isEqualTo(cs.timestamp);
  }

  @Test
  public void authoritativeTimestamp_isNotServerSideOfChildSpan() {
    assertThat(authoritativeTimestamp(span.parentId(2L).addAnnotation(sr).build()))
        .isNull();
  }
}
