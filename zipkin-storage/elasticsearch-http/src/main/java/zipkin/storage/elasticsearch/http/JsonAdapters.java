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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import zipkin.Annotation;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.internal.v2.Span;
import zipkin.internal.V2SpanConverter;

/**
 * Read-only json adapters resurrected from before we switched to Java 6 as storage components can
 * be Java 7+
 */
final class JsonAdapters {
  static final JsonAdapter<zipkin.Span> SPAN_ADAPTER = new JsonAdapter<zipkin.Span>() {
    @Override
    public zipkin.Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.skipValue();
          continue;
        }
        switch (nextName) {
          case "traceId":
            result.traceId(reader.nextString());
            break;
          case "parentId":
            result.parentId(reader.nextString());
            break;
          case "id":
            result.id(reader.nextString());
            break;
          case "kind":
            result.kind(Span.Kind.valueOf(reader.nextString()));
            break;
          case "name":
            result.name(reader.nextString());
            break;
          case "timestamp":
            result.timestamp(reader.nextLong());
            break;
          case "duration":
            result.duration(reader.nextLong());
            break;
          case "localEndpoint":
            result.localEndpoint(ENDPOINT_ADAPTER.fromJson(reader));
            break;
          case "remoteEndpoint":
            result.remoteEndpoint(ENDPOINT_ADAPTER.fromJson(reader));
            break;
          case "annotations":
            reader.beginArray();
            while (reader.hasNext()) {
              Annotation a = ANNOTATION_ADAPTER.fromJson(reader);
              result.addAnnotation(a.timestamp, a.value);
            }
            reader.endArray();
            break;
          case "tags":
            reader.beginObject();
            while (reader.hasNext()) {
              result.putTag(reader.nextName(), reader.nextString());
            }
            reader.endObject();
            break;
          case "debug":
            result.debug(reader.nextBoolean());
            break;
          case "shared":
            result.shared(reader.nextBoolean());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return V2SpanConverter.toSpan(result.build());
    }

    @Override
    public void toJson(JsonWriter writer, zipkin.Span value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override
    public Annotation fromJson(JsonReader reader) throws IOException {
      Annotation.Builder result = Annotation.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "timestamp":
            result.timestamp(reader.nextLong());
            break;
          case "value":
            result.value(reader.nextString());
            break;
          case "endpoint":
            result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, Annotation value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override
    public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.builder().serviceName("");
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.skipValue();
          continue;
        }
        switch (nextName) {
          case "serviceName":
            result.serviceName(reader.nextString());
            break;
          case "ipv4":
          case "ipv6":
            result.parseIp(reader.nextString());
            break;
          case "port":
            result.port(reader.nextInt());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, Endpoint value) throws IOException {
      throw new UnsupportedOperationException();
    }
  }.nullSafe();

  static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {
    @Override
    public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "parent":
            result.parent(reader.nextString());
            break;
          case "child":
            result.child(reader.nextString());
            break;
          case "callCount":
            result.callCount(reader.nextLong());
            break;
          case "errorCount":
            result.errorCount(reader.nextLong());
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, DependencyLink value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };
}

