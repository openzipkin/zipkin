/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static io.zipkin.BinaryAnnotation.Type.STRING;
import static io.zipkin.Constants.CLIENT_SEND;
import static io.zipkin.internal.Util.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class CodecTest {

  protected abstract Codec codec();

  Endpoint web = Endpoint.create("web", (127 << 24) | 1, 8080);

  Span span = new Span.Builder()
      .traceId(123)
      .name("methodcall")
      .id(456)
      .addAnnotation(Annotation.create(1, CLIENT_SEND, web))
      .addAnnotation(Annotation.create(20, CLIENT_SEND, web))
      .addBinaryAnnotation(BinaryAnnotation.create("http.uri", "/foo".getBytes(UTF_8), STRING, web))
      .build();

  @Test
  public void spanRoundTrip() throws IOException {
    byte[] bytes = codec().writeSpan(span);
    assertThat(codec().readSpan(bytes))
        .isEqualTo(span);
  }

  @Test
  public void spanDecodesToNullOnEmpty() throws IOException {
    assertThat(codec().readSpan(new byte[0]))
        .isNull();
  }

  Dependencies dependencies = new Dependencies(0L, TimeUnit.HOURS.toMicros(1), asList(
      new DependencyLink("Gizmoduck", "tflock", 4),
      new DependencyLink("mobileweb", "Gizmoduck", 4),
      new DependencyLink("tfe", "mobileweb", 6)
  ));

  @Test
  public void dependencyLinkRoundTrip() throws IOException {
    byte[] bytes = codec().writeDependencyLink(dependencies.links.get(0));
    assertThat(codec().readDependencyLink(bytes))
        .isEqualTo(dependencies.links.get(0));
  }

  @Test
  public void dependencyLinkDecodesToNullOnEmpty() throws IOException {
    assertThat(codec().readDependencyLink(new byte[0]))
        .isNull();
  }

  @Test
  public void dependenciesRoundTrip() throws IOException {
    byte[] bytes = codec().writeDependencies(dependencies);
    assertThat(codec().readDependencies(bytes))
        .isEqualTo(dependencies);
  }

  @Test
  public void dependenciesDecodeToNullOnEmpty() throws IOException {
    assertThat(codec().readDependencies(new byte[0]))
        .isNull();
  }
}
