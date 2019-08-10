/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import zipkin2.Annotation;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;
import zipkin2.internal.DependencyLinker;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.DB;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.TestObjects.TRACE_DURATION;
import static zipkin2.TestObjects.TRACE_ENDTS;
import static zipkin2.TestObjects.TRACE_STARTTS;
import static zipkin2.TestObjects.midnightUTC;

/**
 * Base test for {@link SpanStore} implementations that support dependency aggregation. Subtypes
 * should create a connection to a real backend, even if that backend is in-process.
 *
 * <p>This is a replacement for {@code zipkin.storage.DependenciesTest}. There is some redundancy
 * as {@code zipkin2.internal.DependencyLinkerTest} also defines many of these tests. The redundancy
 * helps ensure integrated storage doesn't fail due to mismapping of data, for example.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ITDependencies<T extends StorageComponent> extends ITStorage<T> {
  static final Endpoint KAFKA = Endpoint.newBuilder().serviceName("kafka").build();
  static final List<DependencyLink> LINKS = asList(
    DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1L).build(),
    DependencyLink.newBuilder().parent("backend").child("db").callCount(1L).errorCount(1L).build()
  );

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    // Defaults are fine.
  }

  /**
   * Override if dependency processing is a separate job: it should complete before returning from
   * this method.
   */
  protected void processDependencies(List<Span> spans) throws Exception {
    spanConsumer().accept(spans).execute();
  }

  /**
   * Normally, the root-span is where trace id == span id and parent id == null. The default is to
   * look back one day from today.
   */
  @Test void getDependencies() throws Exception {
    processDependencies(TRACE);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute())
      .containsOnlyElementsOf(LINKS);
  }

  /**
   * This tests that dependency linking ignores the high-bits of the trace ID when grouping spans
   * for dependency links. This allows environments with 64-bit instrumentation to participate in
   * the same trace as 128-bit instrumentation.
   */
  @Test void getDependencies_strictTraceId() throws Exception {
    List<Span> mixedTrace = asList(
      Span.newBuilder().traceId("7180c278b62e8f6a216a2aea45d08fc9").id("1").name("get")
        .kind(Kind.SERVER)
        .timestamp(TODAY * 1000L)
        .duration(350 * 1000L)
        .localEndpoint(FRONTEND)
        .build(),
      // the server dropped traceIdHigh
      Span.newBuilder().traceId("216a2aea45d08fc9").parentId("1").id("2").name("get")
        .kind(Kind.SERVER).shared(true)
        .timestamp((TODAY + 100) * 1000L)
        .duration(250 * 1000L)
        .localEndpoint(BACKEND)
        .build(),
      Span.newBuilder().traceId("7180c278b62e8f6a216a2aea45d08fc9").parentId("1").id("2")
        .kind(Kind.CLIENT)
        .timestamp((TODAY + 50) * 1000L)
        .duration(300 * 1000L)
        .localEndpoint(FRONTEND)
        .build()
    );

    processDependencies(mixedTrace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build()
    );
  }

  /** It should be safe to run dependency link jobs twice */
  @Test void replayOverwrites() throws Exception {
    processDependencies(TRACE);
    processDependencies(TRACE);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute())
      .containsOnlyElementsOf(LINKS);
  }

  /** Edge-case when there are no spans, or instrumentation isn't logging annotations properly. */
  @Test void empty() throws Exception {
    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute())
      .isEmpty();
  }

  /**
   * Trace id is not required to be a span id. For example, some instrumentation may create separate
   * trace ids to help with collisions, or to encode information about the origin. This test makes
   * sure we don't rely on the trace id = root span id convention.
   */
  @Test void traceIdIsOpaque() throws Exception {
    List<Span> differentTraceId = TRACE.stream()
      .map(s -> s.toBuilder().traceId("123").build())
      .collect(toList());
    processDependencies(differentTraceId);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute())
      .containsOnlyElementsOf(LINKS);
  }

  /**
   * When all servers are instrumented, they all record {@link Kind#SERVER} and the {@link
   * Span#localEndpoint()} indicates the service.
   */
  @Test void getDependenciesAllInstrumented() throws Exception {
    Endpoint one = Endpoint.newBuilder().serviceName("trace-producer-one").ip("127.0.0.1").build();
    Endpoint onePort3001 = one.toBuilder().port(3001).build();
    Endpoint two = Endpoint.newBuilder().serviceName("trace-producer-two").ip("127.0.0.2").build();
    Endpoint twoPort3002 = two.toBuilder().port(3002).build();
    Endpoint three =
      Endpoint.newBuilder().serviceName("trace-producer-three").ip("127.0.0.3").build();

    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10").name("get")
        .kind(Kind.SERVER)
        .timestamp(TODAY * 1000L)
        .duration(350 * 1000L)
        .localEndpoint(one)
        .build(),
      Span.newBuilder().traceId("10").parentId("10").id("20").name("get")
        .kind(Kind.CLIENT)
        .timestamp((TODAY + 50) * 1000L)
        .duration(250 * 1000L)
        .localEndpoint(onePort3001)
        .build(),
      Span.newBuilder().traceId("10").parentId("10").id("20").name("get").shared(true)
        .kind(Kind.SERVER)
        .timestamp((TODAY + 100) * 1000L)
        .duration(150 * 1000L)
        .localEndpoint(two)
        .build(),
      Span.newBuilder().traceId("10").parentId("20").id("30").name("query")
        .kind(Kind.CLIENT)
        .timestamp((TODAY + 150) * 1000L)
        .duration(50 * 1000L)
        .localEndpoint(twoPort3002)
        .build(),
      Span.newBuilder().traceId("10").parentId("20").id("30").name("query").shared(true)
        .kind(Kind.SERVER)
        .timestamp((TODAY + 160) * 1000L)
        .duration(20 * 1000L)
        .localEndpoint(three)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder()
        .parent("trace-producer-one")
        .child("trace-producer-two")
        .callCount(1)
        .build(),
      DependencyLink.newBuilder()
        .parent("trace-producer-two")
        .child("trace-producer-three")
        .callCount(1)
        .build()
    );
  }

  @Test void dependencies_loopback() throws Exception {
    List<Span> traceWithLoopback = asList(
      TRACE.get(0),
      TRACE.get(1).toBuilder().remoteEndpoint(TRACE.get(0).localEndpoint()).build()
    );

    processDependencies(traceWithLoopback);

    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_DURATION).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("frontend").callCount(1).build()
    );
  }

  /**
   * Some systems log a different trace id than the root span. This seems "headless", as we won't
   * see a span whose id is the same as the trace id.
   */
  @Test void dependencies_headlessTrace() throws Exception {
    ArrayList<Span> trace = new ArrayList<>(TRACE);
    trace.remove(0);
    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute())
      .containsOnlyElementsOf(LINKS);
  }

  @Test void looksBackIndefinitely() throws Exception {
    processDependencies(TRACE);

    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_ENDTS).execute())
      .containsOnlyElementsOf(LINKS);
  }

  /** Ensure complete traces are aggregated, even if they complete after endTs */
  @Test void endTsInsideTheTrace() throws Exception {
    processDependencies(TRACE);

    assertThat(store().getDependencies(TRACE_STARTTS + 100, 200).execute())
      .containsOnlyElementsOf(LINKS);
  }

  @Test void endTimeBeforeData() throws Exception {
    processDependencies(TRACE);

    assertThat(store().getDependencies(TRACE_STARTTS - 1000L, 1000L).execute())
      .isEmpty();
  }

  @Test void lookbackAfterData() throws Exception {
    processDependencies(TRACE);

    assertThat(store().getDependencies(TODAY + 2 * DAY, DAY).execute())
      .isEmpty();
  }

  /**
   * This test confirms that the span store can detect dependency indicated by local and remote
   * endpoint. Specifically, this detects an uninstrumented client before the trace and an
   * uninstrumented server at the end of it.
   */
  @Test void notInstrumentedClientAndServer() throws Exception {
    Endpoint someClient = Endpoint.newBuilder().serviceName("some-client").ip("172.17.0.4").build();

    List<Span> trace = asList(
      Span.newBuilder().traceId("20").id("20").name("get")
        .timestamp(TODAY * 1000L).duration(350L * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(FRONTEND)
        .remoteEndpoint(someClient)
        .build(),
      Span.newBuilder().traceId("20").parentId("20").id("21").name("get")
        .timestamp((TODAY + 50L) * 1000L).duration(250L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .build(),
      Span.newBuilder().traceId("20").parentId("20").id("21").name("get").shared(true)
        .timestamp((TODAY + 250) * 1000L).duration(50L * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND)
        .build(),
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 150L) * 1000L).duration(50L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(BACKEND)
        .remoteEndpoint(DB)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("some-client").child("frontend").callCount(1).build(),
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(1).build()
    );
  }

  @Test void endTsAndLookbackMustBePositive() throws IOException {
    try {
      store().getDependencies(0L, DAY).execute();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("endTs <= 0");
    }

    try {
      store().getDependencies(TRACE_ENDTS, 0L).execute();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("lookback <= 0");
    }
  }

  @Test void instrumentedClientAndServer() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10").name("get")
        .timestamp((TODAY + 50L) * 1000L).duration(250L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .build(),
      Span.newBuilder().traceId("10").id("10").name("get").shared(true)
        .timestamp((TODAY + 100) * 1000L).duration(150L * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND)
        .build(),
      Span.newBuilder().traceId("10").parentId("10").id("11").name("get")
        .timestamp((TODAY + 150L) * 1000L).duration(50L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(BACKEND)
        .remoteEndpoint(DB)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(1).build()
    );
  }

  @Test void instrumentedProducerAndConsumer() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10").name("send")
        .timestamp((TODAY + 50L) * 1000L).duration(1)
        .kind(Kind.PRODUCER)
        .localEndpoint(FRONTEND)
        .remoteEndpoint(KAFKA)
        .build(),
      Span.newBuilder().traceId("10").parentId("10").id("11").name("receive")
        .timestamp((TODAY + 100) * 1000L).duration(1)
        .kind(Kind.CONSUMER)
        .remoteEndpoint(KAFKA)
        .localEndpoint(BACKEND)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("kafka").callCount(1).build(),
      DependencyLink.newBuilder().parent("kafka").child("backend").callCount(1).build()
    );
  }

  /** Ensure there's no query limit problem around links */
  @Test void manyLinks() throws Exception {
    int count = 1000; // Larger than 10, which is the default ES search limit that tripped this
    List<Span> spans = new ArrayList<>(count);
    for (int i = 1; i <= count; i++) {
      Endpoint web = FRONTEND.toBuilder().serviceName("web-" + i).build();
      Endpoint app = BACKEND.toBuilder().serviceName("app-" + i).build();
      Endpoint db = DB.toBuilder().serviceName("db-" + i).build();

      spans.add(Span.newBuilder().traceId(Integer.toHexString(i)).id("10").name("get")
        .timestamp((TODAY + 50L) * 1000L).duration(250L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(web)
        .build()
      );
      spans.add(Span.newBuilder().traceId(Integer.toHexString(i)).id("10").name("get").shared(true)
        .timestamp((TODAY + 100) * 1000L).duration(150 * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(app)
        .build()
      );
      spans.add(
        Span.newBuilder().traceId(Integer.toHexString(i)).parentId("10").id("11").name("get")
          .timestamp((TODAY + 150L) * 1000L).duration(50L * 1000L)
          .kind(Kind.CLIENT)
          .localEndpoint(app)
          .remoteEndpoint(db)
          .build()
      );
    }

    processDependencies(spans);

    List<DependencyLink> links = store().getDependencies(TRACE_ENDTS, DAY).execute();
    assertThat(links).hasSize(count * 2); // web-? -> app-?, app-? -> db-?
    assertThat(links).extracting(DependencyLink::callCount)
      .allSatisfy(callCount -> assertThat(callCount).isEqualTo(1));
  }

  /** This shows a missing parent still results in a dependency link when local endpoints change */
  @Test void missingIntermediateSpan() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("20").id("20").name("get")
        .timestamp(TODAY * 1000L).duration(350L * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(FRONTEND)
        .build(),
      // missing an intermediate span
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 150L) * 1000L).duration(50L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(BACKEND)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build()
    );
  }

  /**
   * This test shows that dependency links can be filtered at daily granularity. This allows the UI
   * to look for dependency intervals besides TODAY.
   */
  @Test void canSearchForIntervalsBesidesToday() throws Exception {
    // Let's pretend we have two days of data processed
    //  - Note: calling this twice allows test implementations to consider timestamps
    processDependencies(subtractDay(TRACE));
    processDependencies(TRACE);

    // A user looks at today's links.
    //  - Note: Using the smallest lookback avoids bumping into implementation around windowing.
    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_DURATION).execute())
      .containsOnlyElementsOf(LINKS);

    // A user compares the links from those a day ago.
    assertThat(store().getDependencies(TRACE_ENDTS - DAY, DAY).execute())
      .containsOnlyElementsOf(LINKS);

    // A user looks at all links since data started
    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_ENDTS).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(2L).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(2L).errorCount(2L).build()
    );
  }

  @Test void spanKindIsNotRequiredWhenEndpointsArePresent() throws Exception {
    Endpoint someClient = Endpoint.newBuilder().serviceName("some-client").ip("172.17.0.4").build();

    List<Span> trace = asList(
      Span.newBuilder().traceId("20").id("20").name("get")
        .timestamp(TODAY * 1000L).duration(350L * 1000L)
        .localEndpoint(someClient)
        .remoteEndpoint(FRONTEND).build(),
      Span.newBuilder().traceId("20").parentId("20").id("21").name("get")
        .timestamp((TODAY + 50) * 1000L).duration(250L * 1000L)
        .localEndpoint(FRONTEND)
        .remoteEndpoint(BACKEND).build(),
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 150) * 1000L).duration(50L * 1000L)
        .localEndpoint(BACKEND)
        .remoteEndpoint(DB).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TODAY + 1000, 1000L).execute()).containsOnly(
      DependencyLink.newBuilder().parent("some-client").child("frontend").callCount(1).build(),
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(1).build()
    );
  }

  @Test void unnamedEndpointsAreSkipped() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("20").id("20").name("get")
        .timestamp(TODAY * 1000L).duration(350L * 1000L)
        .localEndpoint(Endpoint.newBuilder().ip("172.17.0.4").build())
        .remoteEndpoint(FRONTEND).build(),
      Span.newBuilder().traceId("20").parentId("20").id("21").name("get")
        .timestamp((TODAY + 50) * 1000L).duration(250L * 1000L)
        .localEndpoint(FRONTEND)
        .remoteEndpoint(BACKEND).build(),
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 150) * 1000L).duration(50L * 1000L)
        .localEndpoint(BACKEND)
        .remoteEndpoint(DB).build()
    );

    processDependencies(trace);

    // note there is no empty string service names
    assertThat(store().getDependencies(TODAY + 1000, 1000L).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(1).build()
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate spans like the below
   * properly.
   *
   * span1: SR SS span2: intermediate call span3: CS SR SS CR: Dependency 1
   */
  @Test void intermediateSpans() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("20").id("20").name("get")
        .timestamp(TODAY * 1000L).duration(350L * 1000L)
        .kind(Kind.SERVER)
        .localEndpoint(FRONTEND).build(),
      Span.newBuilder().traceId("20").parentId("20").id("21").name("call")
        .timestamp((TODAY + 25) * 1000L).duration(325L * 1000L)
        .localEndpoint(FRONTEND).build(),
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 50) * 1000L).duration(250L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(FRONTEND).build(),
      Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
        .timestamp((TODAY + 100) * 1000L).duration(150 * 1000L).shared(true)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND).build(),
      Span.newBuilder().traceId("20").parentId("22").id(23L).name("call")
        .timestamp((TODAY + 110) * 1000L).duration(130L * 1000L)
        .name("depth4")
        .localEndpoint(BACKEND).build(),
      Span.newBuilder().traceId("20").parentId(23L).id(24L).name("call")
        .timestamp((TODAY + 125) * 1000L).duration(105L * 1000L)
        .name("depth5")
        .localEndpoint(BACKEND).build(),
      Span.newBuilder().traceId("20").parentId(24L).id(25L).name("get")
        .timestamp((TODAY + 150) * 1000L).duration(50L * 1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(BACKEND)
        .remoteEndpoint(DB).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TODAY + 1000, 1000L).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build(),
      DependencyLink.newBuilder().parent("backend").child("db").callCount(1).build()
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate spans like the below
   * properly.
   *
   * span1: SR SS span2: intermediate call span3: CS SR SS CR: Dependency 1
   */
  @Test void duplicateAddress() throws Exception {
    V1SpanConverter converter = V1SpanConverter.create();
    List<Span> trace = new ArrayList<>();
    converter.convert(V1Span.newBuilder().traceId("20").id("20").name("get")
      .timestamp(TODAY * 1000L).duration(350L * 1000L)
      .addAnnotation(TODAY * 1000, "sr", FRONTEND)
      .addAnnotation((TODAY + 350) * 1000, "ss", FRONTEND)
      .addBinaryAnnotation("ca", FRONTEND)
      .addBinaryAnnotation("sa", FRONTEND).build(), trace);
    converter.convert(V1Span.newBuilder().traceId("20").parentId("21").id("22").name("get")
      .timestamp((TODAY + 50) * 1000L).duration(250L * 1000L)
      .addAnnotation((TODAY + 50) * 1000, "cs", FRONTEND)
      .addAnnotation((TODAY + 300) * 1000, "cr", FRONTEND)
      .addBinaryAnnotation("ca", BACKEND)
      .addBinaryAnnotation("sa", BACKEND).build(), trace);

    processDependencies(trace);

    assertThat(store().getDependencies(TODAY + 1000, 1000L).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build()
    );
  }

  /**
   * Span starts on one host and ends on the other. In both cases, a response is neither sent nor
   * received.
   */
  @Test void oneway() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10")
        .timestamp((TODAY + 50) * 1000)
        .kind(Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .build(),
      Span.newBuilder().traceId("10").id("10").shared(true)
        .timestamp((TODAY + 100) * 1000)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND)
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_DURATION).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build()
    );
  }

  /** A timeline annotation named error is not a failed span. A tag/binary annotation is. */
  @Test void annotationNamedErrorIsntError() throws Exception {
    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10")
        .timestamp((TODAY + 50) * 1000)
        .kind(Kind.CLIENT)
        .localEndpoint(FRONTEND)
        .build(),
      Span.newBuilder().traceId("10").id("10").shared(true)
        .timestamp((TODAY + 100) * 1000)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND)
        .addAnnotation((TODAY + 72) * 1000, "error")
        .build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, TRACE_DURATION).execute()).containsOnly(
      DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build()
    );
  }

  /** Async span starts from an uninstrumented source. */
  @Test void oneway_noClient() throws Exception {
    Endpoint kafka = Endpoint.newBuilder().serviceName("kafka").ip("172.17.0.4").build();

    List<Span> trace = asList(
      Span.newBuilder().traceId("10").id("10").name("receive")
        .timestamp(TODAY * 1000)
        .kind(Kind.SERVER)
        .localEndpoint(BACKEND)
        .remoteEndpoint(kafka)
        .build(),
      Span.newBuilder().traceId("10").parentId("10").id("11").name("process")
        .timestamp((TODAY + 25) * 1000L).duration(325L * 1000L)
        .localEndpoint(BACKEND).build()
    );

    processDependencies(trace);

    assertThat(store().getDependencies(TRACE_ENDTS, DAY).execute()).containsOnly(
      DependencyLink.newBuilder().parent("kafka").child("backend").callCount(1).build()
    );
  }

  /** rebases a trace backwards a day with different trace. */
  List<Span> subtractDay(List<Span> trace) {
    long random = new Random().nextLong();
    return trace.stream()
      .map(s -> {
          Span.Builder b = s.toBuilder().traceId(Long.toHexString(random));
          if (s.timestampAsLong() != 0L) b.timestamp(s.timestampAsLong() - (DAY * 1000L));
          s.annotations().forEach(a -> b.addAnnotation(a.timestamp() - (DAY * 1000L), a.value()));
          return b.build();
        }
      ).collect(toList());
  }

  /** Returns links aggregated by midnight */
  protected Map<Long, List<DependencyLink>> aggregateLinks(List<Span> spans) {
    Map<Long, DependencyLinker> midnightToLinker = new LinkedHashMap<>();
    for (List<Span> trace : GroupByTraceId.create(false).map(spans)) {
      long midnightOfTrace = flooredTraceTimestamp(trace);
      DependencyLinker linker = midnightToLinker.get(midnightOfTrace);
      if (linker == null) midnightToLinker.put(midnightOfTrace, (linker = new DependencyLinker()));
      linker.putTrace(trace);
    }
    Map<Long, List<DependencyLink>> result = new LinkedHashMap<>();
    midnightToLinker.forEach((midnight, linker) -> result.put(midnight, linker.link()));
    return result;
  }

  /** gets the timestamp in milliseconds floored to midnight */
  static long flooredTraceTimestamp(List<Span> trace) {
    long midnightOfTrace = Long.MAX_VALUE;
    for (Span span : trace) {
      long currentTs = guessTimestamp(span);
      if (currentTs != 0L && currentTs < midnightOfTrace) {
        midnightOfTrace = midnightUTC(currentTs / 1000);
      }
    }
    assertThat(midnightOfTrace).isNotEqualTo(Long.MAX_VALUE);
    return midnightOfTrace;
  }

  static long guessTimestamp(Span span) {
    if (span.timestampAsLong() != 0L) return span.timestampAsLong();
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) {
        return annotation.timestamp();
      }
    }
    return 0L; // return a timestamp that won't match a query
  }
}
