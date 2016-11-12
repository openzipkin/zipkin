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
package zipkin.storage.elasticsearch;

import org.junit.Test;
import zipkin.Codec;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.TODAY;

public class InternalElasticsearchClientTest {

  @Test
  public void toSpanBytes() {
    Span span = Span.builder().traceId(20L).traceIdHigh(30).id(20L).name("get")
        .timestamp(TODAY * 1000).build();

    byte[] result = InternalElasticsearchClient.toSpanBytes(span, TODAY);

    assertThat(Codec.JSON.readSpan(result))
        .isEqualTo(span);
  }

  @Test
  public void toSpanBytes_timestampMillis() {
    Span span = Span.builder().traceId(20L).id(20L).name("get")
        .timestamp(TODAY * 1000).build();

    byte[] result = InternalElasticsearchClient.toSpanBytes(span, TODAY);

    String json = new String(result);
    assertThat(json)
        .startsWith("{\"timestamp_millis\":" + Long.toString(TODAY) +
            ",\"traceId\":\"0000000000000014\"");

    assertThat(Codec.JSON.readSpan(json.getBytes()))
        .isEqualTo(span); // ignores timestamp_millis field
  }
}
