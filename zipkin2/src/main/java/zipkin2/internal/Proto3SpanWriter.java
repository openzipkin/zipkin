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
package zipkin2.internal;

import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * Everything here assumes the field numbers are less than 16, implying a 1 byte tag.
 */
//@Immutable
public final class Proto3SpanWriter implements Buffer.Writer<Span> {
  @Override public int sizeInBytes(Span value) {
    int sizeInBytes = 2 + value.traceId().length() / 2; // tag + len + 8 or 16 bytes
    if (value.parentId() != null) {
      sizeInBytes += 10; // tag + len + 8 bytes
    }
    sizeInBytes += 10; // tag + len + 8 bytes
    if (value.kind() != null) {
      sizeInBytes += 2; // tag + byte
    }
    if (value.name() != null) {
      sizeInBytes += sizeOfStringField(value.name());
    }
    if (value.timestampAsLong() != 0L) {
      sizeInBytes += 9; // tag + 8 byte number
    }
    long duration = value.durationAsLong();
    if (duration != 0L) {
      sizeInBytes += 1 + Buffer.varintSizeInBytes(duration); // tag + varint
    }
    if (value.localEndpoint() != null) {
      sizeInBytes += sizeOfEndpointField(value.localEndpoint());
    }
    if (value.remoteEndpoint() != null) {
      sizeInBytes += sizeOfEndpointField(value.remoteEndpoint());
    }
    int annotationLength = value.annotations().size();
    if (annotationLength > 0) {
      for (int i = 0; i < annotationLength; i++) {
        sizeInBytes += sizeOfAnnotationField(value.annotations().get(i));
      }
    }
    if (!value.tags().isEmpty()) {
      for (Map.Entry<String, String> entry : value.tags().entrySet()) {
        sizeInBytes += sizeOfMapEntryField(entry.getKey(), entry.getValue());
      }
    }
    if (Boolean.TRUE.equals(value.debug())) {
      sizeInBytes += 2; // tag + byte
    }
    if (Boolean.TRUE.equals(value.shared())) {
      sizeInBytes += 2; // tag + byte
    }
    return sizeInBytes;
  }

  @Override public void write(Span value, Buffer b) {
    throw new UnsupportedOperationException();
  }

  @Override public String toString() {
    return "Span";
  }

  static int sizeOfEndpointField(Endpoint value) {
    int sizeInBytes = 0;
    if (value.serviceName() != null) {
      sizeInBytes += sizeOfStringField(value.serviceName());
    }
    if (value.ipv4() != null) {
      sizeInBytes += 6; // tag + len + 4 bytes
    }
    if (value.ipv6() != null) {
      sizeInBytes += 18; // tag + len + 16 bytes
    }
    if (value.port() != null) {
      sizeInBytes += 1 + Buffer.varintSizeInBytes(value.port()); // tag + varint
    }
    // don't write empty endpoints
    if (sizeInBytes == 0) return 0;
    return 1 + Buffer.varintSizeInBytes(sizeInBytes) + sizeInBytes; // tag + len + bytes
  }

  static int sizeOfAnnotationField(Annotation value) {
    int sizeInBytes = 9; // tag + 8 byte number
    sizeInBytes += sizeOfStringField(value.value());
    return 1 + Buffer.varintSizeInBytes(sizeInBytes) + sizeInBytes; // tag + len + bytes
  }

  /** A map entry is an embedded messages: one for field the key and one for the value */
  static int sizeOfMapEntryField(String key, String value) {
    int sizeInBytes = sizeOfStringField(key);
    sizeInBytes += sizeOfStringField(value);
    return 1 + Buffer.varintSizeInBytes(sizeInBytes) + sizeInBytes; // tag + len + bytes
  }

  static int sizeOfStringField(String string) {
    int sizeInBytes = Buffer.utf8SizeInBytes(string);
    return 1 + Buffer.varintSizeInBytes(sizeInBytes) + sizeInBytes; // tag + len + bytes
  }
}
