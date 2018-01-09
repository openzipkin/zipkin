/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.TestObjects;
import zipkin2.storage.QueryRequest;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;

public class ElasticsearchSpanStoreTest {
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchStorage storage = ElasticsearchStorage.newBuilder()
    .hosts(asList(es.url("").toString()))
    .build();
  ElasticsearchSpanStore spanStore = new ElasticsearchSpanStore(storage);

  @After public void close() throws IOException {
    storage.close();
  }

  @Test public void doesntTruncateTraceIdByDefault() throws Exception {
    es.enqueue(new MockResponse());
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(es.takeRequest().getBody().readUtf8())
      .contains("\"traceId\":\"48fec942f3e78b893041d36dc43227fd\"");
  }

  @Test public void truncatesTraceIdTo16CharsWhenNotStrict() throws Exception {
    storage = storage.toBuilder().strictTraceId(false).build();
    spanStore = new ElasticsearchSpanStore(storage);

    es.enqueue(new MockResponse());
    spanStore.getTrace("48fec942f3e78b893041d36dc43227fd").execute();

    assertThat(es.takeRequest().getBody().readUtf8())
      .contains("\"traceId\":\"3041d36dc43227fd\"");
  }

  @Test public void serviceNames_defaultsTo24HrsAgo_6x() throws Exception {
    es.enqueue(new MockResponse().setBody(TestResponses.SERVICE_NAMES));
    spanStore.getServiceNames().execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test public void spanNames_defaultsTo24HrsAgo_6x() throws Exception {
    es.enqueue(new MockResponse().setBody(TestResponses.SPAN_NAMES));
    spanStore.getSpanNames("foo").execute();

    requestLimitedTo2DaysOfIndices_singleTypeIndex();
  }

  @Test public void searchDisabled_doesntMakeRemoteQueryRequests() throws Exception {
    try (ElasticsearchStorage storage = ElasticsearchStorage.newBuilder()
      .hosts(this.storage.hostsSupplier().get())
      .searchEnabled(false).build()) {

      // skip template check
      ElasticsearchSpanStore spanStore = new ElasticsearchSpanStore(storage);

      assertThat(spanStore.getTraces(
        QueryRequest.newBuilder().endTs(TODAY).lookback(10000L).limit(10).build()
      ).execute()).isEmpty();
      assertThat(spanStore.getServiceNames().execute()).isEmpty();
      assertThat(spanStore.getSpanNames("icecream").execute()).isEmpty();

      assertThat(es.getRequestCount()).isZero();
    }
  }

  private void requestLimitedTo2DaysOfIndices_singleTypeIndex() throws Exception {
    long today = TestObjects.midnightUTC(System.currentTimeMillis());
    long yesterday = today - TimeUnit.DAYS.toMillis(1);

    // 24 hrs ago always will fall into 2 days (ex. if it is 4:00pm, 24hrs ago is a different day)
    String indexesToSearch = ""
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, yesterday)
      + ","
      + storage.indexNameFormatter().formatTypeAndTimestamp(SPAN, today);

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
      .startsWith("/" + indexesToSearch + "/_search");
  }
}
