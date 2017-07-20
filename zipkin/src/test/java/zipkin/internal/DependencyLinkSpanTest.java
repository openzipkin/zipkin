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
import zipkin.Constants;
import zipkin.internal.DependencyLinkSpan.Kind;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyLinkSpanTest {

  @Test
  public void testToString() {
    assertThat(DependencyLinkSpan.builder(0L, 1L, null, 1L).build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"id\": \"0000000000000001\", \"kind\": \"UNKNOWN\"}");

    assertThat(DependencyLinkSpan.builder(0L, 1L, 1L, 2L).build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"parentId\": \"0000000000000001\", \"id\": \"0000000000000002\", \"kind\": \"UNKNOWN\"}");

    assertThat(DependencyLinkSpan.builder(0L, 1L, 1L, 2L)
      .srService("processor")
      .caService("kinesis").build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"parentId\": \"0000000000000001\", \"id\": \"0000000000000002\", \"kind\": \"SERVER\", \"service\": \"processor\", \"peerService\": \"kinesis\"}");

    // It is invalid to log "ca" without "sr", so marked as unknown
    assertThat(DependencyLinkSpan.builder(0L, 1L, 1L, 2L)
      .caService("kinesis").build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"parentId\": \"0000000000000001\", \"id\": \"0000000000000002\", \"kind\": \"UNKNOWN\"}");

    assertThat(DependencyLinkSpan.builder(0L, 1L, 1L, 2L)
      .saService("mysql").build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"parentId\": \"0000000000000001\", \"id\": \"0000000000000002\", \"kind\": \"CLIENT\", \"peerService\": \"mysql\"}");

    // arbitrary 2-sided span
    assertThat(DependencyLinkSpan.builder(0L, 1L, 1L, 2L)
      .caService("shell-script")
      .saService("mysql").build())
      .hasToString(
        "{\"traceId\": \"0000000000000001\", \"parentId\": \"0000000000000001\", \"id\": \"0000000000000002\", \"kind\": \"CLIENT\", \"service\": \"shell-script\", \"peerService\": \"mysql\"}");

    // 128-bit trace ID
    assertThat(DependencyLinkSpan.builder(3L, 1L, null, 1L).build())
      .hasToString(
        "{\"traceId\": \"00000000000000030000000000000001\", \"id\": \"0000000000000001\", \"kind\": \"UNKNOWN\"}");
  }

  @Test
  public void parentAndChildApply() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L).build();
    assertThat(span.parentId).isNull();
    assertThat(span.id).isEqualTo(1L);

    span = DependencyLinkSpan.builder(0L, 1L, 1L, 2L).build();
    assertThat(span.parentId).isEqualTo(1L);
    assertThat(span.id).isEqualTo(2L);
  }

  /** You cannot make a dependency link unless you know the the local or peer service. */
  @Test
  public void whenNoServiceLabelsExist_kindIsUnknown() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L).build();

    assertThat(span.kind).isEqualTo(Kind.UNKNOWN);
    assertThat(span.peerService).isNull();
    assertThat(span.service).isNull();
  }

  @Test
  public void whenOnlyAddressLabelsExist_kindIsClient() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .caService("service1")
      .saService("service2")
      .build();

    assertThat(span.kind).isEqualTo(Kind.CLIENT);
    assertThat(span.service).isEqualTo("service1");
    assertThat(span.peerService).isEqualTo("service2");
  }

  /** The linker is biased towards server spans, or client spans that know the peer service. */
  @Test
  public void whenServerLabelsAreMissing_kindIsUnknownAndLabelsAreCleared() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .caService("service1")
      .build();

    assertThat(span.kind).isEqualTo(Kind.UNKNOWN);
    assertThat(span.service).isNull();
    assertThat(span.peerService).isNull();
  }

  /** {@link Constants#SERVER_RECV} is only applied when the local span is acting as a server */
  @Test
  public void whenSrServiceExists_kindIsServer() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .srService("service")
      .build();

    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service");
    assertThat(span.peerService).isNull();
  }

  /**
   * {@link Constants#CLIENT_ADDR} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test
  public void whenSrAndCaServiceExists_caIsThePeer() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .caService("service1")
      .srService("service2")
      .build();

    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service2");
    assertThat(span.peerService).isEqualTo("service1");
  }

  /**
   * {@link Constants#CLIENT_SEND} indicates the peer, which is a client in the case of a server
   * span
   */
  @Test
  public void whenSrAndCsServiceExists_caIsThePeer() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .csService("service1")
      .srService("service2")
      .build();

    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service2");
    assertThat(span.peerService).isEqualTo("service1");
  }

  /** {@link Constants#CLIENT_ADDR} is more authoritative than {@link Constants#CLIENT_SEND} */
  @Test
  public void whenCrAndCaServiceExists_caIsThePeer() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .csService("foo")
      .caService("service1")
      .srService("service2")
      .build();

    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service2");
    assertThat(span.peerService).isEqualTo("service1");
  }

  @Test
  public void specialCasesFinagleLocalSocketLabeling() {
    // Finagle labels two sides of the same socket ("ca", "sa") with the local service name.
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .caService("service")
      .saService("service")
      .build();

    // When there's no "sr" annotation, we assume it is a client.
    assertThat(span.kind).isEqualTo(Kind.CLIENT);
    assertThat(span.service).isNull();
    assertThat(span.peerService).isEqualTo("service");

    span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .srService("service")
      .caService("service")
      .saService("service")
      .build();

    // When there is an "sr" annotation, we know it is a server
    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service");
    assertThat(span.peerService).isNull();
  }

  /**
   * <p>Dependency linker works backwards: it is easier to treat a "cs" as a server span lacking its
   * caller, than a client span lacking its receiver.
   */
  @Test
  public void csWithoutSaIsServer() {
    DependencyLinkSpan span = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .csService("service1")
      .build();

    assertThat(span.kind).isEqualTo(Kind.SERVER);
    assertThat(span.service).isEqualTo("service1");
    assertThat(span.peerService).isNull();
  }

  /** Service links to empty string are confusing and offer no value. */
  @Test
  public void emptyToNull() {
    DependencyLinkSpan.Builder builder = DependencyLinkSpan.builder(0L, 1L, null, 1L)
      .caService("")
      .csService("")
      .saService("")
      .srService("");

    assertThat(builder.caService).isNull();
    assertThat(builder.csService).isNull();
    assertThat(builder.saService).isNull();
    assertThat(builder.srService).isNull();
  }
}
