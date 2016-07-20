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

import java.util.List;
import org.junit.Test;
import zipkin.DependencyLink;
import zipkin.TestObjects;
import zipkin.internal.DependencyLinkSpan.Kind;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class DependencyLinkerTest {

  @Test
  public void baseCase() {
    assertThat(new DependencyLinker().link()).isEmpty();
  }

  @Test
  public void linksSpans() {
    assertThat(new DependencyLinker().putTrace(TestObjects.TRACE).link()).containsExactly(
        DependencyLink.create("web", "app", 1L),
        DependencyLink.create("app", "db", 1L)
    );
  }

  /**
   * The linker links a directed graph, if the span kind is unknown, we don't know the direction to
   * link.
   */
  @Test
  public void doesntLinkUnknownRootSpans() {
    List<DependencyLinkSpan> unknownRootSpans = asList(
        new DependencyLinkSpan(Kind.UNKNOWN, null, 1L, null, null),
        new DependencyLinkSpan(Kind.UNKNOWN, null, 1L, "server", "client"),
        new DependencyLinkSpan(Kind.UNKNOWN, null, 1L, "client", "server")
    );

    for (DependencyLinkSpan span : unknownRootSpans) {
      assertThat(new DependencyLinker()
          .putTrace(asList(span).iterator()).link())
          .isEmpty();
    }
  }

  /**
   * A root span can be a client-originated trace or a server receipt which knows its peer. In these
   * cases, the peer is known and kind establishes the direction.
   */
  @Test
  public void linksSpansDirectedByKind() {
    List<DependencyLinkSpan> validRootSpans = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "server", "client"),
        new DependencyLinkSpan(Kind.CLIENT, null, 1L, "client", "server")
    );

    for (DependencyLinkSpan span : validRootSpans) {
      assertThat(new DependencyLinker()
          .putTrace(asList(span).iterator()).link())
          .containsOnly(DependencyLink.create("client", "server", 1L));
    }
  }

  @Test
  public void callsAgainstTheSameLinkIncreasesCallCount_span() {
    List<DependencyLinkSpan> trace = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "client", null),
        new DependencyLinkSpan(Kind.CLIENT, 1L, 2L, null, "server"),
        new DependencyLinkSpan(Kind.CLIENT, 1L, 3L, null, "server")
    );

    assertThat(new DependencyLinker()
        .putTrace(trace.iterator()).link())
        .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  @Test
  public void callsAgainstTheSameLinkIncreasesCallCount_trace() {
    List<DependencyLinkSpan> trace = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "client", null),
        new DependencyLinkSpan(Kind.CLIENT, 1L, 2L, null, "server")
    );

    assertThat(new DependencyLinker()
        .putTrace(trace.iterator())
        .putTrace(trace.iterator()).link())
        .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  /**
   * Spans don't always include both the client and server service. When you know the kind, you can
   * link these without duplicating call count.
   */
  @Test
  public void singleHostSpansResultInASingleCallCount() {
    List<List<DependencyLinkSpan>> singleLinks = asList(
        asList(
            new DependencyLinkSpan(Kind.CLIENT, null, 1L, "client", "server"),
            new DependencyLinkSpan(Kind.SERVER, 1L, 2L, "server", null)
        ),
        asList(
            new DependencyLinkSpan(Kind.SERVER, null, 1L, "client", null),
            new DependencyLinkSpan(Kind.CLIENT, 1L, 2L, "client", "server")
        )
    );

    for (List<DependencyLinkSpan> trace : singleLinks) {
      assertThat(new DependencyLinker()
          .putTrace(trace.iterator()).link())
          .containsOnly(DependencyLink.create("client", "server", 1L));
    }
  }

  /**
   * Spans are sometimes intermediated by an unknown type of span. Prefer the nearest server when
   * accounting for them.
   */
  @Test
  public void intermediatedClientSpansMissingLocalServiceNameLinkToNearestServer() {
    List<DependencyLinkSpan> trace = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "client", null),
        new DependencyLinkSpan(Kind.UNKNOWN, 1L, 2L, null, null), // possibly a local fan-out span
        new DependencyLinkSpan(Kind.CLIENT, 2L, 3L, null, "server"),
        new DependencyLinkSpan(Kind.CLIENT, 2L, 4L, null, "server")
    );

    assertThat(new DependencyLinker()
        .putTrace(trace.iterator()).link())
        .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  /** A loopback span is direction-agnostic, so can be linked properly regardless of kind. */
  @Test
  public void linksLoopbackSpans() {
    List<DependencyLinkSpan> validRootSpans = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "service", "service"),
        new DependencyLinkSpan(Kind.CLIENT, null, 1L, "service", "service")
    );

    for (DependencyLinkSpan span : validRootSpans) {
      assertThat(new DependencyLinker()
          .putTrace(asList(span).iterator()).link())
          .containsOnly(DependencyLink.create("service", "service", 1L));
    }
  }

  /**
   * A dependency link is between two services. Given only one span, we cannot link if we don't know
   * both service names.
   */
  @Test
  public void cannotLinkSingleSpanWithoutBothServiceNames() {
    List<DependencyLinkSpan> incompleteRootSpans = asList(
        new DependencyLinkSpan(Kind.SERVER, null, 1L, null, null),
        new DependencyLinkSpan(Kind.SERVER, null, 1L, "server", null),
        new DependencyLinkSpan(Kind.SERVER, null, 1L, null, "client"),
        new DependencyLinkSpan(Kind.CLIENT, null, 1L, null, null),
        new DependencyLinkSpan(Kind.CLIENT, null, 1L, "client", null),
        new DependencyLinkSpan(Kind.CLIENT, null, 1L, null, "server")
    );

    for (DependencyLinkSpan span : incompleteRootSpans) {
      assertThat(new DependencyLinker()
          .putTrace(asList(span).iterator()).link())
          .isEmpty();
    }
  }

  @Test
  public void merge() {
    List<DependencyLink> links = asList(
        DependencyLink.create("client", "server", 2L),
        DependencyLink.create("client", "server", 2L),
        DependencyLink.create("client", "client", 1L)
    );

    assertThat(DependencyLinker.merge(links)).containsExactly(
        DependencyLink.create("client", "server", 4L),
        DependencyLink.create("client", "client", 1L)
    );
  }
}
