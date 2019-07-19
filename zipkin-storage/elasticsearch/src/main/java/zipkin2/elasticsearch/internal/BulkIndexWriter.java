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
package zipkin2.elasticsearch.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static zipkin2.internal.Platform.SHORT_STRING_LENGTH;

public abstract class BulkIndexWriter<T> {

  /**
   * Write a complete json document according to index strategy and returns the ID field.
   */
  public abstract String writeDocument(T input, ByteBufOutputStream sink);

  public static final BulkIndexWriter<Span> SPAN = new BulkIndexWriter<Span>() {
    @Override public String writeDocument(Span input, ByteBufOutputStream sink) {
      return write(input, true, sink);
    }
  };
  public static final BulkIndexWriter<Span>
    SPAN_SEARCH_DISABLED = new BulkIndexWriter<Span>() {
    @Override public String writeDocument(Span input, ByteBufOutputStream sink) {
      return write(input, false, sink);
    }
  };

  public static final BulkIndexWriter<Map.Entry<String, String>> AUTOCOMPLETE =
    new BulkIndexWriter<Map.Entry<String, String>>() {
      @Override public String writeDocument(Map.Entry<String, String> input,
        ByteBufOutputStream sink) {
        try (JsonGenerator writer = JsonSerializers.jsonGenerator(sink)) {
          writeAutocompleteEntry(input.getKey(), input.getValue(), writer);
        } catch (IOException e) {
          throw new AssertionError("Couldn't close generator for a memory stream.", e);
        }
        // Id is used to dedupe server side as necessary. Arbitrarily same format as _q value.
        return input.getKey() + '=' + input.getValue();
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
  static String write(Span span, boolean searchEnabled, ByteBufOutputStream sink) {
    int startIndex = sink.buffer().writerIndex();
    try (JsonGenerator writer = JsonSerializers.jsonGenerator(sink)) {
      writer.writeStartObject();
      if (searchEnabled) addSearchFields(span, writer);
      writer.writeStringField("traceId", span.traceId());
      if (span.parentId() != null) writer.writeStringField("parentId", span.parentId());
      writer.writeStringField("id", span.id());
      if (span.kind() != null) writer.writeStringField("kind", span.kind().toString());
      if (span.name() != null) writer.writeStringField("name", span.name());
      if (span.timestampAsLong() != 0L) {
        writer.writeNumberField("timestamp", span.timestampAsLong());
      }
      if (span.durationAsLong() != 0L) writer.writeNumberField("duration", span.durationAsLong());
      if (span.localEndpoint() != null && !EMPTY_ENDPOINT.equals(span.localEndpoint())) {
        writer.writeFieldName("localEndpoint");
        write(span.localEndpoint(), writer);
      }
      if (span.remoteEndpoint() != null && !EMPTY_ENDPOINT.equals(span.remoteEndpoint())) {
        writer.writeFieldName("remoteEndpoint");
        write(span.remoteEndpoint(), writer);
      }
      if (!span.annotations().isEmpty()) {
        writer.writeArrayFieldStart("annotations");
        for (int i = 0, length = span.annotations().size(); i < length; ) {
          write(span.annotations().get(i++), writer);
        }
        writer.writeEndArray();
      }
      if (!span.tags().isEmpty()) {
        writer.writeObjectFieldStart("tags");
        Iterator<Map.Entry<String, String>> tags = span.tags().entrySet().iterator();
        while (tags.hasNext()) write(tags.next(), writer);
        writer.writeEndObject();
      }
      if (Boolean.TRUE.equals(span.debug())) writer.writeBooleanField("debug", true);
      if (Boolean.TRUE.equals(span.shared())) writer.writeBooleanField("shared", true);
      writer.writeEndObject();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }

    // get a slice representing the document we just wrote so that we can make a content hash
    ByteBuf slice = sink.buffer().slice(startIndex, sink.buffer().writerIndex() - startIndex);

    return span.traceId() + '-' + md5(slice);
  }

  static void writeAutocompleteEntry(String key, String value, JsonGenerator writer) {
    try {
      writer.writeStartObject();
      writer.writeStringField("tagKey", key);
      writer.writeStringField("tagValue", value);
      writer.writeEndObject();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
  }

  static void write(Map.Entry<String, String> tag, JsonGenerator writer) throws IOException {
    writer.writeStringField(tag.getKey(), tag.getValue());
  }

  static void write(Annotation annotation, JsonGenerator writer) throws IOException {
    writer.writeStartObject();
    writer.writeNumberField("timestamp", annotation.timestamp());
    writer.writeStringField("value", annotation.value());
    writer.writeEndObject();
  }

  static void write(Endpoint endpoint, JsonGenerator writer) throws IOException {
    writer.writeStartObject();
    if (endpoint.serviceName() != null) {
      writer.writeStringField("serviceName", endpoint.serviceName());
    }
    if (endpoint.ipv4() != null) writer.writeStringField("ipv4", endpoint.ipv4());
    if (endpoint.ipv6() != null) writer.writeStringField("ipv6", endpoint.ipv6());
    if (endpoint.portAsInt() != 0) writer.writeNumberField("port", endpoint.portAsInt());
    writer.writeEndObject();
  }

  static void addSearchFields(Span span, JsonGenerator writer) throws IOException {
    long timestampMillis = span.timestampAsLong() / 1000L;
    if (timestampMillis != 0L) writer.writeNumberField("timestamp_millis", timestampMillis);
    if (!span.tags().isEmpty() || !span.annotations().isEmpty()) {
      writer.writeArrayFieldStart("_q");
      for (Annotation a : span.annotations()) {
        if (a.value().length() > SHORT_STRING_LENGTH) continue;
        writer.writeString(a.value());
      }
      for (Map.Entry<String, String> tag : span.tags().entrySet()) {
        int length = tag.getKey().length() + tag.getValue().length() + 1;
        if (length > SHORT_STRING_LENGTH) continue;
        writer.writeString(tag.getKey()); // search is possible by key alone
        writer.writeString(tag.getKey() + "=" + tag.getValue());
      }
      writer.writeEndArray();
    }
  }

  static String md5(ByteBuf buf) {
    final MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    }
    messageDigest.update(buf.nioBuffer());
    return ByteBufUtil.hexDump(messageDigest.digest());
  }
}
