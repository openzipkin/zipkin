/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.elasticsearch.internal;

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import okio.Buffer;
import okio.BufferedSink;
import okio.HashingSink;
import okio.Okio;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static zipkin2.internal.Platform.SHORT_STRING_LENGTH;

public abstract class BulkIndexWriter<T> {

  /**
   * Write a complete json document according to index strategy and returns the ID field.
   */
  public abstract String writeDocument(T input, BufferedSink writer);

  public static final BulkIndexWriter<Span> SPAN = new BulkIndexWriter<Span>() {
    @Override public String writeDocument(Span input, BufferedSink sink) {
      return write(input, true, sink);
    }
  };
  public static final BulkIndexWriter<Span>
    SPAN_SEARCH_DISABLED = new BulkIndexWriter<Span>() {
    @Override public String writeDocument(Span input, BufferedSink sink) {
      return write(input, false, sink);
    }
  };

  public static final BulkIndexWriter<Map.Entry<String, String>> AUTOCOMPLETE =
    new BulkIndexWriter<Map.Entry<String, String>>() {
      @Override public String writeDocument(Map.Entry<String, String> input, BufferedSink sink) {
        writeAutocompleteEntry(input.getKey(), input.getValue(), JsonWriter.of(sink));
        // Id is used to dedupe server side as necessary. Arbitrarily same format as _q value.
        return input.getKey() + "=" + input.getValue();
      }
    };

  static final Endpoint EMPTY_ENDPOINT = Endpoint.newBuilder().build();

  /**
   * In order to allow systems like Kibana to search by timestamp, we add a field "timestamp_millis"
   * when storing. The cheapest way to do this without changing the codec is prefixing it to the
   * json. For example. {"traceId":"... becomes {"timestamp_millis":12345,"traceId":"...
   *
   * <p>Tags are stored as a dictionary. Since some tag names will include inconsistent number of
   * dots (ex "error" and perhaps "error.message"), we cannot index them naturally with
   * elasticsearch. Instead, we add an index-only (non-source) field of {@code _q} which includes
   * valid search queries. For example, the tag {@code error -> 500} results in {@code
   * "_q":["error", "error=500"]}. This matches the input query syntax, and can be checked manually
   * with curl.
   *
   * <p>Ex {@code curl -s localhost:9200/zipkin:span-2017-08-11/_search?q=_q:error=500}
   *
   * @param searchEnabled encodes timestamp_millis and _q when non-empty
   */
  static String write(Span span, boolean searchEnabled, BufferedSink sink) {
    HashingSink hashingSink = HashingSink.md5(sink);
    JsonWriter writer = JsonWriter.of(Okio.buffer(hashingSink));
    try {
      writer.beginObject();
      if (searchEnabled) addSearchFields(span, writer);
      writer.name("traceId").value(span.traceId());
      if (span.parentId() != null) writer.name("parentId").value(span.parentId());
      writer.name("id").value(span.id());
      if (span.kind() != null) writer.name("kind").value(span.kind().toString());
      if (span.name() != null) writer.name("name").value(span.name());
      if (span.timestampAsLong() != 0L) writer.name("timestamp").value(span.timestampAsLong());
      if (span.durationAsLong() != 0L) writer.name("duration").value(span.durationAsLong());
      if (span.localEndpoint() != null && !EMPTY_ENDPOINT.equals(span.localEndpoint())) {
        writer.name("localEndpoint");
        write(span.localEndpoint(), writer);
      }
      if (span.remoteEndpoint() != null && !EMPTY_ENDPOINT.equals(span.remoteEndpoint())) {
        writer.name("remoteEndpoint");
        write(span.remoteEndpoint(), writer);
      }
      if (!span.annotations().isEmpty()) {
        writer.name("annotations");
        writer.beginArray();
        for (int i = 0, length = span.annotations().size(); i < length; ) {
          write(span.annotations().get(i++), writer);
        }
        writer.endArray();
      }
      if (!span.tags().isEmpty()) {
        writer.name("tags");
        writer.beginObject();
        Iterator<Map.Entry<String, String>> tags = span.tags().entrySet().iterator();
        while (tags.hasNext()) write(tags.next(), writer);
        writer.endObject();
      }
      if (Boolean.TRUE.equals(span.debug())) writer.name("debug").value(true);
      if (Boolean.TRUE.equals(span.shared())) writer.name("shared").value(true);
      writer.endObject();
      writer.flush();
      hashingSink.flush();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
    return new Buffer()
      .writeUtf8(span.traceId()).writeByte('-').writeUtf8(hashingSink.hash().hex())
      .readUtf8();
  }

  static void writeAutocompleteEntry(String key, String value, JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("tagKey").value(key);
      writer.name("tagValue").value(value);
      writer.endObject();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
  }

  static void write(Map.Entry<String, String> tag, JsonWriter writer) throws IOException {
    writer.name(tag.getKey()).value(tag.getValue());
  }

  static void write(Annotation annotation, JsonWriter writer) throws IOException {
    writer.beginObject();
    writer.name("timestamp").value(annotation.timestamp());
    writer.name("value").value(annotation.value());
    writer.endObject();
  }

  static void write(Endpoint endpoint, JsonWriter writer) throws IOException {
    writer.beginObject();
    if (endpoint.serviceName() != null) writer.name("serviceName").value(endpoint.serviceName());
    if (endpoint.ipv4() != null) writer.name("ipv4").value(endpoint.ipv4());
    if (endpoint.ipv6() != null) writer.name("ipv6").value(endpoint.ipv6());
    if (endpoint.portAsInt() != 0) writer.name("port").value(endpoint.portAsInt());
    writer.endObject();
  }

  static void addSearchFields(Span span, JsonWriter writer) throws IOException {
    long timestampMillis = span.timestampAsLong() / 1000L;
    if (timestampMillis != 0L) writer.name("timestamp_millis").value(timestampMillis);
    if (!span.tags().isEmpty() || !span.annotations().isEmpty()) {
      writer.name("_q");
      writer.beginArray();
      for (Annotation a : span.annotations()) {
        if (a.value().length() > SHORT_STRING_LENGTH) continue;
        writer.value(a.value());
      }
      for (Map.Entry<String, String> tag : span.tags().entrySet()) {
        int length = tag.getKey().length() + tag.getValue().length() + 1;
        if (length > SHORT_STRING_LENGTH) continue;
        writer.value(tag.getKey()); // search is possible by key alone
        writer.value(tag.getKey() + "=" + tag.getValue());
      }
      writer.endArray();
    }
  }
}
