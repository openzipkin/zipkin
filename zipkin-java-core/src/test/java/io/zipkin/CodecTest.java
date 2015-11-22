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
import java.util.List;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class CodecTest {

  protected abstract Codec codec();

  @Test
  public void spanRoundTrip() throws IOException {
    for (Span span : TestObjects.TRACE) {
      byte[] bytes = codec().writeSpan(span);
      assertThat(codec().readSpan(bytes))
          .isEqualTo(span);
    }
  }

  @Test
  public void spanDecodesToNullOnEmpty() throws IOException {
    assertThat(codec().readSpan(new byte[0]))
        .isNull();
  }

  @Test
  public void spansRoundTrip() throws IOException {
    byte[] bytes = codec().writeSpans(TestObjects.TRACE);
    assertThat(codec().readSpans(bytes))
        .isEqualTo(TestObjects.TRACE);
  }

  @Test
  public void writeTraces() throws IOException {
    byte[] bytes = codec().writeTraces(asList(TestObjects.TRACE, TestObjects.TRACE));
    assertThat(codec().writeSpans(TestObjects.TRACE).length * 2)
        .isLessThan(bytes.length);
  }

  @Test
  public void spansDecodeToNullOnEmpty() throws IOException {
    assertThat(codec().readSpans(new byte[0]))
        .isNull();
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test
  public void spansDecodeToNullOnMalformed() throws IOException {
    assertThat(codec().readSpans(new byte[]{'h', 'e', 'l', 'l', 'o'}))
        .isNull();
  }

  @Test
  public void dependencyLinksRoundTrip() throws IOException {
    List<DependencyLink> links = asList(
        DependencyLink.create("foo", "bar", 2),
        DependencyLink.create("bar", "baz", 3)
    );
    byte[] bytes = codec().writeDependencyLinks(links);
    assertThat(codec().readDependencyLinks(bytes))
        .isEqualTo(links);
  }

  @Test
  public void dependencyLinksDecodeToNullOnEmpty() throws IOException {
    assertThat(codec().readDependencyLinks(new byte[0]))
        .isNull();
  }
}
