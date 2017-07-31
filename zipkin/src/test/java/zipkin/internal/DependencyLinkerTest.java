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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.Test;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.Span2.Kind;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.ERROR;

public class DependencyLinkerTest {
  List<String> messages = new ArrayList<>();

  Logger logger = new Logger("", null) {
    {
      setLevel(Level.ALL);
    }

    @Override public void log(Level level, String msg) {
      assertThat(level).isEqualTo(Level.FINE);
      messages.add(msg);
    }
  };

  @Test
  public void baseCase() {
    assertThat(new DependencyLinker().link()).isEmpty();
  }

  @Test
  public void linksSpans() {
    assertThat(new DependencyLinker().putTrace(TestObjects.TRACE).link()).containsExactly(
      DependencyLink.builder().parent("web").child("app").callCount(1L).build(),
      DependencyLink.builder().parent("app").child("db").callCount(1L).errorCount(1L).build()
    );
  }

  @Test
  public void dropsSelfReferencingSpans() {
    List<Span> trace = TestObjects.TRACE.stream()
      .map(s -> s.toBuilder().parentId(s.parentId != null ? s.id : null).build())
      .collect(Collectors.toList());

    assertThat(new DependencyLinker(logger).putTrace(trace).link()).isEmpty();

    assertThat(messages).contains(
      "skipping circular dependency: traceId=f66529c8cc356aa0, spanId=93288b464457044e",
      "skipping circular dependency: traceId=f66529c8cc356aa0, spanId=71e62981f1e136a7"
    );
  }

  /**
   * A root span can be a client-originated trace or a server receipt which knows its peer. In these
   * cases, the peer is known and kind establishes the direction.
   */
  @Test
  public void linksSpansDirectedByKind() {
    List<Span2> validRootSpans = asList(
      span2(1L, null, 1L, Kind.SERVER, "server", "client", false),
      span2(1L, null, 1L, Kind.CLIENT, "client", "server", false)
    );

    for (Span2 span : validRootSpans) {
      assertThat(new DependencyLinker()
        .putTrace(asList(span).iterator()).link())
        .containsOnly(DependencyLink.create("client", "server", 1L));
    }
  }

  @Test
  public void callsAgainstTheSameLinkIncreasesCallCount_span() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.SERVER, "client", null, false),
      span2(1L, 1L, 2L, Kind.CLIENT, null, "server", false),
      span2(1L, 1L, 3L, Kind.CLIENT, null, "server", false)
    );

    assertThat(new DependencyLinker()
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  @Test
  public void callsAgainstTheSameLinkIncreasesCallCount_trace() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.SERVER, "client", null, false),
      span2(1L, 1L, 2L, Kind.CLIENT, null, "server", false)
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
    List<Span2> trace = asList(
      span2(3L, null, 3L, Kind.CLIENT, "client", null, false),
      span2(3L, 3L, 4L, Kind.SERVER, "server", null, false)
    );

    assertThat(new DependencyLinker()
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 1L));
  }

  @Test
  public void singleHostSpansResultInASingleErrorCount() {
    List<Span2> trace = asList(
      span2(3L, null, 3L, Kind.CLIENT, "client", null, true),
      span2(3L, 3L, 4L, Kind.SERVER, "server", null, true)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("client").child("server").callCount(1L).errorCount(1L).build()
    );
  }

  @Test
  public void singleHostSpansResultInASingleErrorCount_sameId() {
    List<Span2> trace = asList(
      span2(3L, null, 3L, Kind.CLIENT, "client", null, true),
      span2(3L, null, 3L, Kind.SERVER, "server", null, true)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("client").child("server").callCount(1L).errorCount(1L).build()
    );
  }

  @Test
  public void singleHostSpansResultInASingleCallCount_defersNameToServer() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.CLIENT, "client", "server", false),
      span2(1L, 1L, 2L, Kind.SERVER, "server", null, false)
    );

    assertThat(new DependencyLinker(logger)
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 1L));

    assertThat(messages).contains("deferring link to rpc child span");
    messages.clear();
  }

  @Test
  public void singleHostSpans_multipleChildren() {
    List<Span2> trace = asList(
      span2(4L, null, 4L, Kind.CLIENT, "client", null, false),
      span2(4L, 4L, 5L, Kind.SERVER, "server", "client", true),
      span2(4L, 4L, 6L, Kind.SERVER, "server", "client", false)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("client").child("server").callCount(2L).errorCount(1L).build()
    );
  }

  @Test
  public void singleHostSpans_multipleChildren_defersNameToServer() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.CLIENT, "client", "server", false),
      span2(1L, 1L, 2L, Kind.SERVER, "server", null, false),
      span2(1L, 1L, 3L, Kind.SERVER, "server", null, false)
    );

    assertThat(new DependencyLinker(logger)
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 2L));

    assertThat(messages).contains("deferring link to rpc child span");
  }

  /**
   * Spans are sometimes intermediated by an unknown type of span. Prefer the nearest server when
   * accounting for them.
   */
  @Test
  public void intermediatedClientSpansMissingLocalServiceNameLinkToNearestServer() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.SERVER, "client", null, false),
      span2(1L, 1L, 2L, null, null, null, false),
      // possibly a local fan-out span
      span2(1L, 2L, 3L, Kind.CLIENT, "server", null, false),
      span2(1L, 2L, 4L, Kind.CLIENT, "server", null, false)
    );

    assertThat(new DependencyLinker()
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  @Test
  public void errorsOnUninstrumentedLinks() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.SERVER, "client", null, false),
      span2(1L, 1L, 2L, null, null, null, false),
      // there's no remote here, so we shouldn't see any error count
      span2(1L, 2L, 3L, Kind.CLIENT, "server", null, true),
      span2(1L, 2L, 4L, Kind.CLIENT, "server", null, true)
    );

    assertThat(new DependencyLinker()
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("client", "server", 2L));
  }

  @Test
  public void errorsOnInstrumentedLinks() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.SERVER, "foo", null, false),
      span2(1L, 1L, 2L, null, null, null, false),
      span2(1L, 2L, 3L, Kind.CLIENT, "bar", "baz", true),
      span2(1L, 2L, 4L, Kind.CLIENT, "bar", "baz", false)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("foo").child("bar").callCount(2L).build(),
      DependencyLink.builder().parent("bar").child("baz").callCount(2L).errorCount(1L).build()
    );
  }

  @Test
  public void linkWithErrorIsLogged() {
    List<Span2> trace = asList(
      span2(1L, 2L, 3L, Kind.CLIENT, "foo", "bar", true)
    );
    new DependencyLinker(logger).putTrace(trace.iterator()).link();

    assertThat(messages).contains(
      "incrementing error link foo -> bar"
    );
  }

  /** Tag indicates a failed span, not an annotation */
  @Test
  public void annotationNamedErrorDoesntIncrementErrorCount() {
    List<Span2> trace = asList(
      span2(1L, 2L, 3L, Kind.CLIENT, "foo", "bar", false)
        .toBuilder().addAnnotation(1L, ERROR).build()
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("foo").child("bar").callCount(1L).build()
    );
  }

  /** A loopback span is direction-agnostic, so can be linked properly regardless of kind. */
  @Test
  public void linksLoopbackSpans() {
    List<Span2> validRootSpans = asList(
      span2(1L, null, 1L, Kind.SERVER, "service", "service", false),
      span2(2L, null, 2L, Kind.CLIENT, "service", "service", false)
    );

    for (Span2 span : validRootSpans) {
      assertThat(new DependencyLinker()
        .putTrace(asList(span).iterator()).link())
        .containsOnly(DependencyLink.create("service", "service", 1L));
    }
  }

  @Test
  public void noSpanKindTreatedSameAsClient() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, null, "some-client", "web", false),
      span2(1L, 1L, 2L, null, "web", "app", false),
      span2(1L, 2L, 3L, null, "app", "db", false)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.create("some-client", "web", 1L),
      DependencyLink.create("web", "app", 1L),
      DependencyLink.create("app", "db", 1L)
    );
  }

  @Test
  public void noSpanKindWithError() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, null, "some-client", "web", false),
      span2(1L, 1L, 2L, null, "web", "app", true),
      span2(1L, 2L, 3L, null, "app", "db", false)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.create("some-client", "web", 1L),
      DependencyLink.builder().parent("web").child("app").callCount(1L).errorCount(1L).build(),
      DependencyLink.create("app", "db", 1L)
    );
  }

  /**
   * A dependency link is between two services. Given only one span, we cannot link if we don't know
   * both service names.
   */
  @Test
  public void cannotLinkSingleSpanWithoutBothServiceNames() {
    List<Span2> incompleteRootSpans = asList(
      span2(1L, null, 1L, Kind.SERVER, null, null, false),
      span2(1L, null, 1L, Kind.SERVER, "server", null, false),
      span2(1L, null, 1L, Kind.SERVER, null, "client", false),
      span2(1L, null, 1L, Kind.CLIENT, null, null, false),
      span2(1L, null, 1L, Kind.CLIENT, "client", null, false),
      span2(1L, null, 1L, Kind.CLIENT, null, "server", false)
    );

    for (Span2 span : incompleteRootSpans) {
      assertThat(new DependencyLinker(logger)
        .putTrace(asList(span).iterator()).link())
        .isEmpty();
    }
  }

  @Test
  public void doesntLinkUnrelatedSpansWhenMissingRootSpan() {
    long missingParentId = 1;
    List<Span2> trace = asList(
      span2(1L, missingParentId, 2L, Kind.SERVER, "service1", null, false),
      span2(1L, missingParentId, 3L, Kind.SERVER, "service2", null, false)
    );

    assertThat(new DependencyLinker(logger)
      .putTrace(trace.iterator()).link())
      .isEmpty();

    assertThat(messages).contains(
      "skipping synthetic node for broken span tree"
    );
  }

  @Test
  public void linksRelatedSpansWhenMissingRootSpan() {
    long missingParentId = 1;
    List<Span2> trace = asList(
      span2(1L, missingParentId, 2L, Kind.SERVER, "service1", null, false),
      span2(1L, 2L, 3L, Kind.SERVER, "service2", null, false)
    );

    assertThat(new DependencyLinker(logger)
      .putTrace(trace.iterator()).link())
      .containsOnly(DependencyLink.create("service1", "service2", 1L));

    assertThat(messages).contains(
      "skipping synthetic node for broken span tree"
    );
  }

  /** Client+Server spans that don't share IDs are treated as server spans missing their peer */
  @Test
  public void linksSingleHostSpans() {
    List<Span2> singleHostSpans = asList(
      span2(1L, null, 1L, Kind.CLIENT, "web", null, false),
      span2(1L, 1L, 2L, Kind.SERVER, "app", null, false)
    );

    assertThat(new DependencyLinker()
      .putTrace(singleHostSpans.iterator()).link())
      .containsOnly(DependencyLink.create("web", "app", 1L));
  }

  @Test
  public void linksSingleHostSpans_errorOnClient() {
    List<Span2> trace = asList(
      span2(1L, null, 1L, Kind.CLIENT, "web", null, true),
      span2(1L, 1L, 2L, Kind.SERVER, "app", null, false)
    );

    assertThat(new DependencyLinker().putTrace(trace.iterator()).link()).containsOnly(
      DependencyLink.builder().parent("web").child("app").callCount(1L).errorCount(1L).build()
    );
  }

  /** Creates a link when there's a span missing, in this case 2L which is an RPC from web to app */
  @Test
  public void missingSpan() {
    List<Span2> singleHostSpans = asList(
      span2(1L, null, 1L, Kind.SERVER, "web", null, false),
      span2(1L, 1L, 2L, Kind.CLIENT, "app", null, false)
    );

    assertThat(new DependencyLinker(logger)
      .putTrace(singleHostSpans.iterator()).link())
      .containsOnly(DependencyLink.create("web", "app", 1L));

    assertThat(messages).contains(
      "detected missing link to client span"
    );
  }

  @Test
  public void merge() {
    List<DependencyLink> links = asList(
      DependencyLink.builder().parent("foo").child("bar").callCount(2L).errorCount(1L).build(),
      DependencyLink.builder().parent("foo").child("bar").callCount(2L).errorCount(2L).build(),
      DependencyLink.builder().parent("foo").child("foo").callCount(1L).build()
    );

    assertThat(DependencyLinker.merge(links)).containsExactly(
      DependencyLink.builder().parent("foo").child("bar").callCount(4L).errorCount(3L).build(),
      DependencyLink.builder().parent("foo").child("foo").callCount(1L).build()
    );
  }

  @Test
  public void merge_error() {
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

  static Span2 span2(long traceId, @Nullable Long parentId, long id, @Nullable Kind kind,
    @Nullable String local, @Nullable String remote, boolean isError) {
    Span2.Builder result = Span2.builder();
    result.traceId(traceId).parentId(parentId).id(id).kind(kind);
    if (local != null) result.localEndpoint(Endpoint.builder().serviceName(local).build());
    if (remote != null) result.remoteEndpoint(Endpoint.builder().serviceName(remote).build());
    if (isError) result.putTag(ERROR, "");
    return result.build();
  }
}
