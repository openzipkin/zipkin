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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.internal.CallbackCaptor;
import zipkin.internal.Util;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.storage.elasticsearch.http.TestResponses.SERVICE_NAMES;
import static zipkin.storage.elasticsearch.http.TestResponses.SPAN_NAMES;

public class LegacyElasticsearchHttpSpanStoreTest {
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchStorage storage = ElasticsearchHttpStorage.builder()
    .hosts(asList(es.url("").toString()))
    .build().delegate;
  LegacyElasticsearchHttpSpanStore spanStore = new LegacyElasticsearchHttpSpanStore(storage);

  @After public void close() throws IOException {
    storage.close();
  }

  @Test public void serviceNames_defaultsTo24HrsAgo() throws Exception {
    es.enqueue(new MockResponse().setBody(SERVICE_NAMES));
    spanStore.getServiceNames(new CallbackCaptor<>());

    requestLimitedTo2DaysOfIndices_multiTypeIndex();
  }

  @Test public void spanNames_defaultsTo24HrsAgo() throws Exception {
    es.enqueue(new MockResponse().setBody(SPAN_NAMES));
    spanStore.getSpanNames("foo", new CallbackCaptor<>());

    requestLimitedTo2DaysOfIndices_multiTypeIndex();
  }

  private void requestLimitedTo2DaysOfIndices_multiTypeIndex() throws Exception {
    long today = Util.midnightUTC(System.currentTimeMillis());
    long yesterday = today - TimeUnit.DAYS.toMillis(1);

    // 24 hrs ago always will fall into 2 days (ex. if it is 4:00pm, 24hrs ago is a different day)
    String indexesToSearch = ""
      + storage.indexNameFormatter().formatTypeAndTimestamp(null, yesterday)
      + ","
      + storage.indexNameFormatter().formatTypeAndTimestamp(null, today);

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
      .startsWith("/" + indexesToSearch + "/servicespan/_search");
  }
}
