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
package zipkin2.elasticsearch;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import zipkin2.Annotation;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;

/**
 * Read-only json adapters resurrected from before we switched to Java 6 as storage components can
 * be Java 7+
 */
final class JsonAdapters {
  static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override public Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.newBuilder();
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
              result.addAnnotation(a.timestamp(), a.value());
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
      return result.build();
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable Span value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override public Annotation fromJson(JsonReader reader) throws IOException {
      reader.beginObject();
      Long timestamp = null;
      String value = null;
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "timestamp":
            timestamp = reader.nextLong();
            break;
          case "value":
            value = reader.nextString();
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      if (timestamp == null || value == null) {
        throw new IllegalStateException("Incomplete annotation at " + reader.getPath());
      }
      return Annotation.create(timestamp, value);
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable Annotation value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };

  static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override public Endpoint fromJson(JsonReader reader) throws IOException {
      reader.beginObject();
      String serviceName = null, ipv4 = null, ipv6 = null;
      Integer port = null;
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peek() == JsonReader.Token.NULL) {
          reader.skipValue();
          continue;
        }
        switch (nextName) {
          case "serviceName":
            serviceName = reader.nextString();
            break;
          case "ipv4":
            ipv4 = reader.nextString();
            break;
          case "ipv6":
            ipv6 = reader.nextString();
            break;
          case "port":
            port = reader.nextInt();
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      if (serviceName == null && ipv4 == null && ipv6 == null && port == null) {
        throw new IllegalStateException("Incomplete endpoint at " + reader.getPath());
      }
      return Endpoint.newBuilder().serviceName(serviceName).ip(ipv4).ip(ipv6).port(port).build();
    }

    @Override
    public void toJson(JsonWriter writer, @Nullable Endpoint value) throws IOException {
      throw new UnsupportedOperationException();
    }
  }.nullSafe();

  static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {
    @Override public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.newBuilder();
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
    public void toJson(JsonWriter writer, @Nullable DependencyLink value) throws IOException {
      throw new UnsupportedOperationException();
    }
  };
}

