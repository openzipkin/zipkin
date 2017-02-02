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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Codec;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.Util.UTF_8;

public class HttpBulkSpanIndexerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  @Rule
  public MockWebServer es = new MockWebServer();

  CallbackCaptor<Void> callback = new CallbackCaptor<>();

  ElasticsearchHttpStorage storage = ElasticsearchHttpStorage.builder()
      .hosts(asList(es.url("").toString()))
      .build();

  HttpBulkSpanIndexer indexer = new HttpBulkSpanIndexer(storage);

  @After
  public void close() throws IOException {
    storage.close();
  }

  @Test
  public void prefixWithTimestampMillis() {
    Span span = Span.builder().traceId(20L).id(20L).name("get")
        .timestamp(TODAY * 1000).build();

    byte[] result =
        HttpBulkSpanIndexer.prefixWithTimestampMillis(Codec.JSON.writeSpan(span), TODAY);

    String json = new String(result);
    assertThat(json)
        .startsWith("{\"timestamp_millis\":" + Long.toString(TODAY) + ",\"traceId\":");

    assertThat(Codec.JSON.readSpan(json.getBytes()))
        .isEqualTo(span); // ignores timestamp_millis field
  }

  @Test
  public void doesntWriteSpanId() throws Exception {
    es.enqueue(new MockResponse());

    indexer.add("test_zipkin_http-2016-10-01", TestObjects.LOTS_OF_SPANS[0], (Long) null);
    indexer.execute(callback);
    callback.get();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getBody().readByteString().utf8())
        .doesNotContain("\"_id\"");
  }

  @Test
  public void writesSpanNaturallyWhenNoTimestamp() throws Exception {
    es.enqueue(new MockResponse());

    indexer.add("test_zipkin_http-2016-10-01", TestObjects.LOTS_OF_SPANS[0], (Long) null);
    indexer.execute(callback);
    callback.get();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getBody().readByteString().utf8())
        .endsWith(new String(Codec.JSON.writeSpan(TestObjects.LOTS_OF_SPANS[0]), UTF_8) + "\n");
  }

  @Test
  public void addsPipelineId() throws Exception {
    close();

    indexer = new HttpBulkSpanIndexer(storage = ElasticsearchHttpStorage.builder()
        .hosts(asList(es.url("").toString()))
        .pipeline("zipkin")
        .build());

    es.enqueue(new MockResponse());

    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    indexer
        .add("zipkin-2016-10-01", TestObjects.TRACE.get(0), (Long) null)
        .execute(callback);
    callback.get();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
        .isEqualTo("/_bulk?pipeline=zipkin");
  }
}
