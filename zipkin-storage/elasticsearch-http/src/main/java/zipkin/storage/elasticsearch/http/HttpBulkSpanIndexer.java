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

import zipkin.Codec;
import zipkin.Span;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static zipkin.storage.elasticsearch.InternalElasticsearchClient.toSpanBytes;

final class HttpBulkSpanIndexer extends HttpBulkIndexer<Span> implements
    InternalElasticsearchClient.BulkSpanIndexer {

  HttpBulkSpanIndexer(HttpClient delegate, String spanType) {
    super(delegate, spanType);
  }

  @Override
  public HttpBulkSpanIndexer add(String index, Span span, Long timestampMillis) {
    String id = null; // Allow ES to choose an ID
    if (timestampMillis == null) {
      super.add(index, span, id);
      return this;
    }
    writeIndexMetadata(index, id);
    body.write(toSpanBytes(span, timestampMillis));
    body.writeByte('\n');

    if (client.flushOnWrites) indices.add(index);
    return this;
  }

  @Override byte[] toJsonBytes(Span span) {
    return Codec.JSON.writeSpan(span);
  }
}
