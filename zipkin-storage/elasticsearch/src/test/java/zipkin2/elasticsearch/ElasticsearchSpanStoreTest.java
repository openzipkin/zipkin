/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.TestObjects;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.elasticsearch.VersionSpecificTemplates.TYPE_SPAN;

class ElasticsearchSpanStoreTest {
  static final AggregatedHttpResponse EMPTY_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.empty());

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  @BeforeEach void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri())).build();
    spanStore = new ElasticsearchSpanStore(storage);
  }

  @AfterEach void tearDown() throws IOException {
    storage.close();
  }

  ElasticsearchStorage storage;
  ElasticsearchSpanStore spanStore;

  @Test void doesntTruncateTraceIdByDefault() throws Exception {
    server.enqueue(EMPTY_RESPONSE);
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(server.takeRequest().request().contentUtf8())
      .contains("\"traceId\":\"48fec942f3e78b893041d36dc43227fd\"");
  }

  @Test void truncatesTraceIdTo16CharsWhenNotStrict() throws Exception {
    storage.close();
    storage = storage.toBuilder().strictTraceId(false).build();
    spanStore = new ElasticsearchSpanStore(storage);

    server.enqueue(EMPTY_RESPONSE);
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(server.takeRequest().request().contentUtf8())
      .contains("\"traceId\":\"3041d36dc43227fd\"");
  }

  @Test void serviceNames_defaultsTo24HrsAgo_6x() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, TestResponses.SERVICE_NAMES));
    spanStore.getServiceNames().execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test void spanNames_defaultsTo24HrsAgo_6x() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, TestResponses.SPAN_NAMES));
    spanStore.getSpanNames("foo").execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test void searchDisabled_doesntMakeRemoteQueryRequests() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .searchEnabled(false)
      .build();

    // skip template check
    ElasticsearchSpanStore spanStore = new ElasticsearchSpanStore(storage);

    QueryRequest request = QueryRequest.newBuilder().endTs(TODAY).lookback(DAY).limit(10).build();
    assertThat(spanStore.getTraces(request).execute()).isEmpty();
    assertThat(spanStore.getServiceNames().execute()).isEmpty();
    assertThat(spanStore.getSpanNames("icecream").execute()).isEmpty();

    assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
  }

  void requestLimitedTo2DaysOfIndices_singleTypeIndex() {
    long today = TestObjects.midnightUTC(System.currentTimeMillis());
    long yesterday = today - TimeUnit.DAYS.toMillis(1);

    // 24 hrs ago always will fall into 2 days (ex. if it is 4:00pm, 24hrs ago is a different day)
    String indexesToSearch = ""
      + storage.indexNameFormatter().formatTypeAndTimestamp(TYPE_SPAN, yesterday)
      + ","
      + storage.indexNameFormatter().formatTypeAndTimestamp(TYPE_SPAN, today);

    AggregatedHttpRequest request = server.takeRequest().request();
    assertThat(request.path()).startsWith("/" + indexesToSearch + "/_search");
  }
}
