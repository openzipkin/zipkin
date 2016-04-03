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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.CallbackCaptor;
import zipkin.internal.Dependencies;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.internal.Util.midnightUTC;

/**
 * Base test for {@link SpanStore} implementations that support dependency aggregation. Subtypes
 * should create a connection to a real backend, even if that backend is in-process.
 *
 * <p/>This is a replacement for {@code com.twitter.zipkin.storage.DependencyStoreSpec}.
 */
public abstract class DependenciesTest {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  SpanStore store() {
    return storage().spanStore();
  }

  /** Clears store between tests. */
  @Before
  public abstract void clear();

  /**
   * Override if dependency processing is a separate job: it should complete before returning from
   * this method.
   */
  protected void processDependencies(List<Span> spans) {
    // Blocks until the callback completes to allow read-your-writes consistency during tests.
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    storage().asyncSpanConsumer(Sampler.ALWAYS_SAMPLE).accept(spans, captor);
    captor.get(); // block on result
  }

  /** Notably, the cassandra implementation has day granularity */
  protected long day = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

  // Use real time, as most span-stores have TTL logic which looks back several days.
  protected long today = midnightUTC(System.currentTimeMillis());

  Endpoint zipkinWeb = Endpoint.create("zipkin-web", 172 << 24 | 17 << 16 | 3, 8080);
  Endpoint zipkinQuery = Endpoint.create("zipkin-query", 172 << 24 | 17 << 16 | 2, 9411);
  Endpoint zipkinQueryNoPort = new Endpoint.Builder(zipkinQuery).port(null).build();
  Endpoint zipkinJdbc = Endpoint.create("zipkin-jdbc", 172 << 24 | 17 << 16 | 2, 0);

  List<Span> trace = asList(
      new Span.Builder().traceId(1L).id(1L).name("get")
          .addAnnotation(Annotation.create(today * 1000, SERVER_RECV, zipkinWeb))
          .addAnnotation(Annotation.create((today + 350) * 1000, SERVER_SEND, zipkinWeb))
          .build(),
      new Span.Builder().traceId(1L).parentId(1L).id(2L).name("get")
          .addAnnotation(Annotation.create((today + 50) * 1000, CLIENT_SEND, zipkinWeb))
          .addAnnotation(Annotation.create((today + 100) * 1000, SERVER_RECV, zipkinQueryNoPort))
          .addAnnotation(Annotation.create((today + 250) * 1000, SERVER_SEND, zipkinQueryNoPort))
          .addAnnotation(Annotation.create((today + 300) * 1000, CLIENT_RECV, zipkinWeb))
          .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinWeb))
          .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinQuery))
          .build(),
      new Span.Builder().traceId(1L).parentId(2L).id(3L).name("query")
          .addAnnotation(Annotation.create((today + 150) * 1000, CLIENT_SEND, zipkinQuery))
          .addAnnotation(Annotation.create((today + 200) * 1000, CLIENT_RECV, zipkinQuery))
          .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinQuery))
          .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinJdbc))
          .build()
  ).stream().map(ApplyTimestampAndDuration::apply).collect(toList());

  Dependencies dep = Dependencies.create(today, today + 1000, asList(
      new DependencyLink("zipkin-web", "zipkin-query", 1),
      new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
  ));

  /**
   * Normally, the root-span is where trace id == span id and parent id == null. The default is to
   * look back one day from today.
   */
  @Test
  public void getDependencies() {
    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnlyElementsOf(dep.links);
  }

  /** Edge-case when there are no spans, or instrumentation isn't logging annotations properly. */
  @Test
  public void empty() {
    assertThat(store().getDependencies(today + 1000L, null))
        .isEmpty();
  }

  /**
   * Trace id is not required to be a span id. For example, some instrumentation may create separate
   * trace ids to help with collisions, or to encode information about the origin. This test makes
   * sure we don't rely on the trace id = root span id convention.
   */
  @Test
  public void traceIdIsOpaque() {
    List<Span> differentTraceId = trace.stream()
        .map(s -> new Span.Builder(s).traceId(Long.MAX_VALUE).build())
        .collect(toList());
    processDependencies(differentTraceId);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnlyElementsOf(dep.links);
  }

  /**
   * When all servers are instrumented, they all log a {@link Constants#SERVER_RECV ("sr")}
   * annotation, indicating the service.
   */
  @Test
  public void getDependenciesAllInstrumented() {
    Endpoint one = Endpoint.create("trace-producer-one", 127 << 24 | 1, 9410);
    Endpoint onePort3001 = new Endpoint.Builder(one).port((short) 3001).build();
    Endpoint two = Endpoint.create("trace-producer-two", 127 << 24 | 2, 9410);
    Endpoint twoPort3002 = new Endpoint.Builder(two).port((short) 3002).build();
    Endpoint three = Endpoint.create("trace-producer-three", 127 << 24 | 3, 9410);

    List<Span> trace = asList(
        new Span.Builder().traceId(10L).id(10L).name("get")
            .timestamp(1445136539256150L).duration(1152579L)
            .addAnnotation(Annotation.create(1445136539256150L, SERVER_RECV, one))
            .addAnnotation(Annotation.create(1445136540408729L, SERVER_SEND, one))
            .build(),
        new Span.Builder().traceId(10L).parentId(10L).id(20L).name("get")
            .timestamp(1445136539764798L).duration(639337L)
            .addAnnotation(Annotation.create(1445136539764798L, CLIENT_SEND, onePort3001))
            .addAnnotation(Annotation.create(1445136539816432L, SERVER_RECV, two))
            .addAnnotation(Annotation.create(1445136540401414L, SERVER_SEND, two))
            .addAnnotation(Annotation.create(1445136540404135L, CLIENT_RECV, onePort3001))
            .build(),
        new Span.Builder().traceId(10L).parentId(20L).id(30L).name("get")
            .timestamp(1445136540025751L).duration(371298L)
            .addAnnotation(Annotation.create(1445136540025751L, CLIENT_SEND, twoPort3002))
            .addAnnotation(Annotation.create(1445136540072846L, SERVER_RECV, three))
            .addAnnotation(Annotation.create(1445136540394644L, SERVER_SEND, three))
            .addAnnotation(Annotation.create(1445136540397049L, CLIENT_RECV, twoPort3002))
            .build()
    );
    processDependencies(trace);

    long traceDuration = trace.get(0).duration;

    assertThat(
        store().getDependencies((trace.get(0).timestamp + traceDuration) / 1000, traceDuration / 1000)
    ).containsOnly(
        new DependencyLink("trace-producer-one", "trace-producer-two", 1),
        new DependencyLink("trace-producer-two", "trace-producer-three", 1)
    );
  }

  /**
   * The primary annotation used in the dependency graph is [[Constants.SERVER_RECV]]
   */
  @Test
  public void getDependenciesMultiLevel() {
    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnlyElementsOf(dep.links);
  }

  @Test
  public void dependencies_loopback() {
    List<Span> traceWithLoopback = asList(
        trace.get(0),
        new Span.Builder(trace.get(1))
            .annotations(trace.get(1).annotations.stream()
                .map(a -> Annotation.create(a.timestamp, a.value, zipkinWeb)).collect(toList()))
            .binaryAnnotations(asList())
            .build());

    processDependencies(traceWithLoopback);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnly(new DependencyLink("zipkin-web", "zipkin-web", 1));
  }

  /**
   * Some systems log a different trace id than the root span. This seems "headless", as we won't
   * see a span whose id is the same as the trace id.
   */
  @Test
  public void dependencies_headlessTrace() {
    processDependencies(asList(trace.get(1), trace.get(2)));

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnlyElementsOf(dep.links);
  }

  @Test
  public void looksBackIndefinitely() {
    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnlyElementsOf(dep.links);
  }

  @Test
  public void insideTheInterval() {
    processDependencies(trace);

    assertThat(store().getDependencies(dep.endTs, dep.endTs - dep.startTs))
        .containsOnlyElementsOf(dep.links);
  }

  @Test
  public void endTimeBeforeData() {
    processDependencies(trace);

    assertThat(store().getDependencies(today - day, null))
        .isEmpty();
  }

  @Test
  public void lookbackAfterData() {
    processDependencies(trace);

    assertThat(store().getDependencies(today + 2 * day, day))
        .isEmpty();
  }

  /**
   * This test confirms that the span store can detect dependency indicated by SERVER_ADDR and
   * CLIENT_ADDR. In some cases an RPC call is made where one of the two services is not
   * instrumented. However, if the other service is able to emit "sa" or "ca" annotation with a
   * service name, the link can still be constructed.
   *
   * span1: CA SR SS: Dependency 1 by a not-instrumented client span2: intermediate call span3: CS
   * CR SA: Dependency 2 to a not-instrumented server
   */
  @Test
  public void notInstrumentedClientAndServer() {
    Endpoint someClient = Endpoint.create("some-client", 172 << 24 | 17 << 16 | 4, 80);

    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, SERVER_SEND, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, someClient))
            .build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("get")
            .timestamp((today + 50L) * 1000).duration(250L * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 100) * 1000, SERVER_RECV, zipkinQuery))
            .addAnnotation(Annotation.create((today + 250) * 1000, SERVER_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 300) * 1000, CLIENT_RECV, zipkinWeb))
            .build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 150L) * 1000).duration(50L * 1000)
            .addAnnotation(Annotation.create((today + 150) * 1000, CLIENT_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 200) * 1000, CLIENT_RECV, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinJdbc))
            .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000L, null)).containsOnly(
        new DependencyLink("some-client", "zipkin-web", 1),
        new DependencyLink("zipkin-web", "zipkin-query", 1),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
    );
  }

  /**
   * This test confirms that the span store can detect dependency indicated by SERVER_RECV or
   * SERVER_ADDR only. Some of implementations such as finagle don't send CLIENT_SEND and
   * CLIENT_ADDR annotations as desired. However, if there is a SERVER_RECV or SERVER_ADDR
   * annotation in the trace tree, the link can still be constructed.
   *
   * span1: SR SS: parent service span2: SA: Dependency 1
   *
   * Currently, the standard implentation can't detect a link with intermediate spans that should be
   * detected.
   *
   * span1: SR SS: parent service span2: intermediate call span3: SR SS: Dependency 1 not detectable
   * in the implementation
   */
  @Test
  public void noClientSendAddrAnnotations() {

    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, SERVER_SEND, zipkinWeb))
            .binaryAnnotations(asList( // finagle also sends SA/CA itself
                BinaryAnnotation.address(SERVER_ADDR, zipkinWeb),
                BinaryAnnotation.address(CLIENT_ADDR, zipkinWeb)))
            .build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("get")
            .timestamp((today + 150L) * 1000).duration(50L * 1000)
            .addAnnotation(Annotation.create((today + 150) * 1000, CLIENT_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 200) * 1000, CLIENT_RECV, zipkinQuery))
            .binaryAnnotations(asList( // finagle also no SR on some condition and CA with itself
                BinaryAnnotation.address(SERVER_ADDR, zipkinQuery),
                BinaryAnnotation.address(CLIENT_ADDR, zipkinQuery)))
            .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000L, null))
        .containsOnly(new DependencyLink("zipkin-web", "zipkin-query", 1));
  }

  /**
   * This test shows that dependency links can be filtered at daily granularity. This allows the UI
   * to look for dependency intervals besides today.
   */
  @Test
  public void canSearchForIntervalsBesidesToday() {
    // Let's pretend we have two days of data processed
    //  - Note: calling this twice allows test implementations to consider timestamps
    processDependencies(subtractDay(trace));
    processDependencies(trace);

    // A user looks at today's links.
    //  - Note: Using the smallest lookback avoids bumping into implementation around windowing.
    assertThat(store().getDependencies(dep.endTs, dep.endTs - dep.startTs))
        .containsOnlyElementsOf(dep.links);

    // A user compares the links from those a day ago.
    assertThat(store().getDependencies(dep.endTs - day, dep.endTs - dep.startTs))
        .containsOnlyElementsOf(dep.links);

    // A user looks at all links since data started
    assertThat(store().getDependencies(dep.endTs, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 2),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 2)
    );
  }

  /** This test confirms that core ("sr", "cs", "cr", "ss") annotations are not required. */
  @Test
  public void noCoreAnnotations() {
    Endpoint someClient = Endpoint.create("some-client", 172 << 24 | 17 << 16 | 4, 80);
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, someClient))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 150) * 1000).duration(50L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinJdbc)).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000, null)).containsOnly(
        new DependencyLink("some-client", "zipkin-web", 1),
        new DependencyLink("zipkin-web", "zipkin-query", 1),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate spans like the below
   * properly.
   *
   * span1: SR SS span2: intermediate call span3: CS SR SS CR: Dependency 1
   */
  @Test
  public void intermediateSpans() {
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, SERVER_SEND, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("call")
            .timestamp((today + 25) * 1000).duration(325L * 1000)
            .addBinaryAnnotation(
                BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth2", zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 100) * 1000, SERVER_RECV, zipkinQuery))
            .addAnnotation(Annotation.create((today + 250) * 1000, SERVER_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 300) * 1000, CLIENT_RECV, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(22L).id(23L).name("call")
            .timestamp((today + 110) * 1000).duration(130L * 1000)
            .addBinaryAnnotation(
                BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth4", zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(23L).id(24L).name("call")
            .timestamp((today + 125) * 1000).duration(105L * 1000)
            .addBinaryAnnotation(
                BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth5", zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(24L).id(25L).name("get")
            .timestamp((today + 150) * 1000).duration(50L * 1000)
            .addAnnotation(Annotation.create((today + 150) * 1000, CLIENT_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 200) * 1000, CLIENT_RECV, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinJdbc)).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 1),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate spans like the below
   * properly.
   *
   * span1: SR SS span2: intermediate call span3: CS SR SS CR: Dependency 1
   */
  @Test
  public void duplicateAddress() {
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, SERVER_SEND, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 300) * 1000, CLIENT_RECV, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinQuery)).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 1)
    );
  }

  @Test
  public void unmergedSpans() {
    List<Span> trace = asList(
        new Span.Builder().traceId(1L).parentId(1L).id(2L).name("get").timestamp((today + 100) * 1000)
            .addAnnotation(Annotation.create((today + 100) * 1000, SERVER_RECV, zipkinQueryNoPort))
            .addAnnotation(Annotation.create((today + 250) * 1000, SERVER_SEND, zipkinQueryNoPort))
            .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, zipkinWeb))
            .build(),
        new Span.Builder().traceId(1L).parentId(1L).id(2L).name("get").timestamp((today + 50) * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 300) * 1000, CLIENT_RECV, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, zipkinQuery))
            .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(today + 1000, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 1)
    );
  }

  /** rebases a trace backwards a day with different trace and span id. */
  List<Span> subtractDay(List<Span> trace) {
    return trace.stream()
        .map(s -> new Span.Builder(s)
            .traceId(s.traceId + 100)
            .parentId(s.parentId != null ? s.parentId + 100 : null)
            .id(s.id + 100)
            .timestamp(s.timestamp != null ? s.timestamp - (day * 1000) : null)
            .annotations(s.annotations.stream()
                .map(a -> Annotation.create(a.timestamp - (day * 1000), a.value, a.endpoint))
                .collect(toList()))
            .build()
        ).collect(toList());
  }
}
