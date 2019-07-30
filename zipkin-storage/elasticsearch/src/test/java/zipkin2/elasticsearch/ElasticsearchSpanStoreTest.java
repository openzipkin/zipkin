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
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.TestObjects;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;

public class ElasticsearchSpanStoreTest {

  static final AtomicReference<AggregatedHttpRequest> CAPTURED_REQUEST =
    new AtomicReference<>();
  static final AtomicReference<AggregatedHttpResponse> MOCK_RESPONSE =
    new AtomicReference<>();
  static final AggregatedHttpResponse EMPTY_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.EMPTY_DATA);

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.serviceUnder("/", (ctx, req) -> HttpResponse.from(
        req.aggregate().thenApply(agg -> {
          CAPTURED_REQUEST.set(agg);
          return HttpResponse.of(MOCK_RESPONSE.get());
        })));
    }
  };

  @Before public void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/"))).build();
    spanStore = new ElasticsearchSpanStore(storage);
  }

  @After public void tearDown() throws IOException {
    storage.close();

    MOCK_RESPONSE.set(null);
    CAPTURED_REQUEST.set(null);
  }

  ElasticsearchStorage storage;
  ElasticsearchSpanStore spanStore;

  @Test public void doesntTruncateTraceIdByDefault() throws Exception {
    MOCK_RESPONSE.set(EMPTY_RESPONSE);
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(CAPTURED_REQUEST.get().contentUtf8())
      .contains("\"traceId\":\"48fec942f3e78b893041d36dc43227fd\"");
  }

  @Test public void truncatesTraceIdTo16CharsWhenNotStrict() throws Exception {
    storage.close();
    storage = storage.toBuilder().strictTraceId(false).build();
    spanStore = new ElasticsearchSpanStore(storage);

    MOCK_RESPONSE.set(EMPTY_RESPONSE);
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(CAPTURED_REQUEST.get().contentUtf8()).contains("\"traceId\":\"3041d36dc43227fd\"");
  }

  @Test public void serviceNames_defaultsTo24HrsAgo_6x() throws Exception {
    MOCK_RESPONSE.set(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, TestResponses.SERVICE_NAMES));
    spanStore.getServiceNames().execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test public void spanNames_defaultsTo24HrsAgo_6x() throws Exception {
    MOCK_RESPONSE.set(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, TestResponses.SPAN_NAMES));
    spanStore.getSpanNames("foo").execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test public void searchDisabled_doesntMakeRemoteQueryRequests() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .searchEnabled(false)
      .build();

    // skip template check
    ElasticsearchSpanStore spanStore = new ElasticsearchSpanStore(storage);

    QueryRequest request = QueryRequest.newBuilder().endTs(TODAY).lookback(DAY).limit(10).build();
    assertThat(spanStore.getTraces(request).execute()).isEmpty();
    assertThat(spanStore.getServiceNames().execute()).isEmpty();
    assertThat(spanStore.getSpanNames("icecream").execute()).isEmpty();

    assertThat(CAPTURED_REQUEST.get()).isNull();
  }

  void requestLimitedTo2DaysOfIndices_singleTypeIndex() throws Exception {
    long today = TestObjects.midnightUTC(System.currentTimeMillis());
    long yesterday = today - TimeUnit.DAYS.toMillis(1);

    // 24 hrs ago always will fall into 2 days (ex. if it is 4:00pm, 24hrs ago is a different day)
    String indexesToSearch = ""
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, yesterday)
      + ","
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, today);

    AggregatedHttpRequest request = CAPTURED_REQUEST.get();
    assertThat(request.path()).startsWith("/" + indexesToSearch + "/_search");
  }
}
