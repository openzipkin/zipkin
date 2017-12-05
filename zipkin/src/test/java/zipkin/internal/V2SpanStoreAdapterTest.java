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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanStore;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin.TestObjects.TODAY;

public class V2SpanStoreAdapterTest {
  @Rule public MockitoRule mocks = MockitoJUnit.rule();
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock SpanStore spanStore;
  @Mock Call call;
  @Mock Callback callback;

  Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2);
  Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 3);
  Span.Builder builder = Span.newBuilder()
    .traceId("7180c278b62e8f6a5b4185666d50f68b")
    .id("5b4185666d50f68b")
    .name("get");

  List<Span> skewedTrace2 = asList(
    builder.clone()
      .kind(Span.Kind.CLIENT)
      .localEndpoint(frontend.toV2())
      .timestamp((TODAY + 200) * 1000)
      .duration(120_000L)
      .build(),
    builder.clone()
      .kind(Span.Kind.SERVER)
      .shared(true)
      .localEndpoint(backend.toV2())
      .timestamp((TODAY + 100) * 1000) // received before sent!
      .duration(60_000L)
      .build()
  );

  List<zipkin.Span> rawSkewedTrace = asList(
    zipkin.Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .timestamp((TODAY + 200) * 1000) // retains client timestamp/duration
      .duration(120_000L)
      .addAnnotation(Annotation.create((TODAY + 200) * 1000, Constants.CLIENT_SEND, frontend))
      .addAnnotation(Annotation.create((TODAY + 320) * 1000, Constants.CLIENT_RECV, frontend))
      .build(),
    zipkin.Span.builder()
      .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
      .traceId(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
      .name("get")
      .addAnnotation(Annotation.create((TODAY + 100) * 1000, Constants.SERVER_RECV, backend))
      .addAnnotation(Annotation.create((TODAY + 160) * 1000, Constants.SERVER_SEND, backend))
      .build()
  );

  List<zipkin.Span> adjustedTrace = asList(zipkin.Span.builder()
    .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
    .traceId(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
    .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
    .name("get")
    .timestamp((TODAY + 200) * 1000) // retains client timestamp/duration
    .duration(120_000L)
    .addAnnotation(Annotation.create((TODAY + 200) * 1000, Constants.CLIENT_SEND, frontend))
    .addAnnotation(Annotation.create((TODAY + 230) * 1000, Constants.SERVER_RECV, backend))
    .addAnnotation(Annotation.create((TODAY + 290) * 1000, Constants.SERVER_SEND, backend))
    .addAnnotation(Annotation.create((TODAY + 320) * 1000, Constants.CLIENT_RECV, frontend))
    .build());

  V2SpanStoreAdapter adapter;

  @Before public void setUp() {
    adapter = new V2SpanStoreAdapter(spanStore);
  }

  @Test public void getTraces_sync_callsExecute() throws IOException {
    when(spanStore.getTraces(any(zipkin2.storage.QueryRequest.class)))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getTraces(QueryRequest.builder().build()))
      .isEmpty();

    verify(call).execute();
  }

  @Test(expected = UncheckedIOException.class)
  public void getTraces_sync_wrapsIOE() throws IOException {
    when(spanStore.getTraces(any(zipkin2.storage.QueryRequest.class)))
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getTraces(QueryRequest.builder().build());
  }

  @Test public void getTraces_async_callsEnqueue() {
    when(spanStore.getTraces(any(zipkin2.storage.QueryRequest.class)))
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getTraces(QueryRequest.builder().build(), callback);

    verify(callback).onSuccess(Collections.emptyList());
  }

  @Test public void getTraces_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getTraces(any(zipkin2.storage.QueryRequest.class)))
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getTraces(QueryRequest.builder().build(), callback);

    verify(callback).onError(throwable);
  }

  @Test public void getTrace_sync_callsExecute() throws IOException {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getTrace(3L, 4L))
      .isNull();

    verify(call).execute();
  }

  @Test(expected = UncheckedIOException.class)
  public void getTrace_sync_wrapsIOE() throws IOException {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getTrace(3L, 4L);
  }

  @Test public void getTrace_async_callsEnqueue() {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getTrace(3L, 4L, callback);

    verify(callback).onSuccess(null);
  }

  @Test public void getTrace_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getTrace(3L, 4L, callback);

    verify(callback).onError(throwable);
  }

  @Test public void getRawTrace_sync_callsExecute() throws IOException {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getRawTrace(3L, 4L))
      .isNull();

    verify(call).execute();
  }

  @Test(expected = UncheckedIOException.class)
  public void getRawTrace_sync_wrapsIOE() throws IOException {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getRawTrace(3L, 4L);
  }

  @Test public void getRawTrace_async_callsEnqueue() {
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getRawTrace(3L, 4L, callback);

    verify(callback).onSuccess(null);
  }

  @Test public void getRawTrace_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getTrace("00000000000000030000000000000004"))
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getRawTrace(3L, 4L, callback);

    verify(callback).onError(throwable);
  }

  @Test public void getServiceNames_sync_callsExecute() throws IOException {
    when(spanStore.getServiceNames())
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getServiceNames())
      .isEmpty();

    verify(call).execute();
  }

  @Test public void getServiceNames_sortsList() throws IOException {
    when(spanStore.getServiceNames())
      .thenReturn(call);
    when(call.execute())
      .thenReturn(asList("foo", "bar"));

    assertThat(adapter.getServiceNames())
      .containsExactly("bar", "foo");
  }

  @Test(expected = UncheckedIOException.class)
  public void getServiceNames_sync_wrapsIOE() throws IOException {
    when(spanStore.getServiceNames())
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getServiceNames();
  }

  @Test public void getServiceNames_async_callsEnqueue() {
    when(spanStore.getServiceNames())
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getServiceNames(callback);

    verify(callback).onSuccess(Collections.emptyList());
  }

  @Test public void getServiceNames_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getServiceNames())
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getServiceNames(callback);

    verify(callback).onError(throwable);
  }

  @Test public void getSpanNames_sync_callsExecute() throws IOException {
    when(spanStore.getSpanNames("service1"))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getSpanNames("service1"))
      .isEmpty();

    verify(call).execute();
  }

  @Test public void getSpanNames_sortsList() throws IOException {
    when(spanStore.getSpanNames("service1"))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(asList("foo", "bar"));

    assertThat(adapter.getSpanNames("service1"))
      .containsExactly("bar", "foo");
  }

  @Test(expected = UncheckedIOException.class)
  public void getSpanNames_sync_wrapsIOE() throws IOException {
    when(spanStore.getSpanNames("service1"))
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getSpanNames("service1");
  }

  @Test public void getSpanNames_async_callsEnqueue() {
    when(spanStore.getSpanNames("service1"))
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getSpanNames("service1", callback);

    verify(callback).onSuccess(Collections.emptyList());
  }

  @Test public void getSpanNames_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getSpanNames("service1"))
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getSpanNames("service1", callback);

    verify(callback).onError(throwable);
  }

  @Test public void getDependencies_sync_callsExecute() throws IOException {
    when(spanStore.getDependencies(3L, 2L))
      .thenReturn(call);
    when(call.execute())
      .thenReturn(Collections.emptyList());

    assertThat(adapter.getDependencies(3L, 2L))
      .isEmpty();

    verify(call).execute();
  }

  @Test(expected = UncheckedIOException.class)
  public void getDependencies_sync_wrapsIOE() throws IOException {
    when(spanStore.getDependencies(3L, 2L))
      .thenReturn(call);
    when(call.execute())
      .thenThrow(IOException.class);

    adapter.getDependencies(3L, 2L);
  }

  @Test public void getDependencies_async_callsEnqueue() {
    when(spanStore.getDependencies(3L, 2L))
      .thenReturn(call);
    doEnqueue(c -> c.onSuccess(Collections.emptyList()));

    adapter.getDependencies(3L, 2L, callback);

    verify(callback).onSuccess(Collections.emptyList());
  }

  @Test public void getDependencies_async_doesntWrapIOE() {
    IOException throwable = new IOException();
    when(spanStore.getDependencies(3L, 2L))
      .thenReturn(call);
    doEnqueue(c -> c.onError(throwable));

    adapter.getDependencies(3L, 2L, callback);

    verify(callback).onError(throwable);
  }

  @Test public void getTracesMapper_adjustsTraces() {
    assertThat(V2SpanStoreAdapter.GetTracesMapper.INSTANCE.map(asList(skewedTrace2)))
      .containsOnly(adjustedTrace); // merged and clock-skew corrected
  }

  @Test public void getTracesMapper_descendingOrder() {
    assertThat(V2SpanStoreAdapter.GetTracesMapper.INSTANCE.map(asList(
      asList(builder.traceId("1").timestamp((TODAY + 1) * 1000).build()),
      asList(builder.traceId("2").timestamp((TODAY + 2) * 1000).build())
    ))).flatExtracting(s -> s)
      .extracting(s -> s.timestamp)
      .containsExactly((TODAY + 2) * 1000, (TODAY + 1) * 1000);
  }

  @Test public void getTraceMapper_adjustsTrace() {
    assertThat(V2SpanStoreAdapter.GetTraceMapper.INSTANCE.map(skewedTrace2))
      .isEqualTo(adjustedTrace); // merged and clock-skew corrected
  }

  @Test public void getTraceMapper_emptyToNull() {
    assertThat(V2SpanStoreAdapter.GetTraceMapper.INSTANCE.map(Collections.emptyList()))
      .isNull();
  }

  @Test public void getRawTraceMapper_doesntAdjustTrace() {
    assertThat(V2SpanStoreAdapter.GetRawTraceMapper.INSTANCE.map(skewedTrace2))
      .isEqualTo(rawSkewedTrace); // merged and clock-skew corrected
  }

  @Test public void getRawTraceMapper_emptyToNull() {
    assertThat(V2SpanStoreAdapter.GetRawTraceMapper.INSTANCE.map(Collections.emptyList()))
      .isNull();
  }

  @Test public void convert_queryRequest() {
    assertThat(V2SpanStoreAdapter.convertRequest(QueryRequest.builder()
      .serviceName("service")
      .spanName("span")
      .parseAnnotationQuery("annotation and tag=value")
      .minDuration(1L)
      .maxDuration(2L)
      .endTs(1000L)
      .lookback(60L)
      .limit(100)
      .build()))
      .isEqualTo(zipkin2.storage.QueryRequest.newBuilder()
        .serviceName("service")
        .spanName("span")
        .parseAnnotationQuery("annotation and tag=value")
        .minDuration(1L)
        .maxDuration(2L)
        .endTs(1000L)
        .lookback(60L)
        .limit(100)
        .build());
  }

  void doEnqueue(Consumer<zipkin2.Callback> answer) {
    doAnswer(invocation -> {
      answer.accept((zipkin2.Callback) invocation.getArguments()[0]);
      return invocation;
    }).when(call).enqueue(any(zipkin2.Callback.class));
  }
}
