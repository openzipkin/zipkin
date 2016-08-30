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
package zipkin;

import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.internal.JsonCodec;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class CodecTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
  public void sizeInBytes() throws IOException {
    for (Span span : TestObjects.TRACE) {
      assertThat(codec().sizeInBytes(span))
          .isEqualTo(codec().writeSpan(span).length);
    }
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
  public void dependencyLinkRoundTrip() throws IOException {
    DependencyLink link = DependencyLink.create("foo", "bar", 2);
    byte[] bytes = codec().writeDependencyLink(link);
    assertThat(codec().readDependencyLink(bytes))
        .isEqualTo(link);
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
  public void decentErrorMessageOnEmptyInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading Span");

    codec().readSpan(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnEmptyInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<Span>");

    codec().readSpans(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnEmptyInput_dependencyLinks() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<DependencyLink>");

    codec().readDependencyLinks(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnMalformedInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading Span from ");

    codec().readSpan(new byte[]{'h', 'e', 'l', 'l', 'o'});
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test
  public void decentErrorMessageOnMalformedInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<Span> from ");

    codec().readSpans(new byte[]{'h', 'e', 'l', 'l', 'o'});
  }

  @Test
  public void decentErrorMessageOnMalformedInput_dependencyLinks() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<DependencyLink> from ");

    codec().readDependencyLinks(new byte[]{'h', 'e', 'l', 'l', 'o'});
  }
}
