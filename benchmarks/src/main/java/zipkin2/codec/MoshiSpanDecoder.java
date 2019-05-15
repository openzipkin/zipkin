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
package zipkin2.codec;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Timeout;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchAccess;

/**
 * Read-only json adapters resurrected from before we switched to Java 6 as storage components can
 * be Java 7+
 */
public final class MoshiSpanDecoder {
  final JsonAdapter<List<Span>> listSpansAdapter;

  public static MoshiSpanDecoder create() {
    return new MoshiSpanDecoder();
  }

  MoshiSpanDecoder() {
    listSpansAdapter = new Moshi.Builder()
      .add(Span.class, ElasticsearchAccess.jsonSpanAdapter())
      .build().adapter(Types.newParameterizedType(List.class, Span.class));
  }

  public List<Span> decodeList(byte[] spans) {
    BufferedSource source = new Buffer().write(spans);
    try {
      return listSpansAdapter.fromJson(source);
    } catch (IOException e) {
      throw new AssertionError(e); // no I/O
    }
  }

  public List<Span> decodeList(ByteBuffer spans) {
    try {
      return listSpansAdapter.fromJson(JsonReader.of(Okio.buffer(new ByteBufferSource(spans))));
    } catch (IOException e) {
      throw new AssertionError(e); // no I/O
    }
  }

  final class ByteBufferSource implements okio.Source {
    final ByteBuffer source;

    final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();

    ByteBufferSource(ByteBuffer source) {
      this.source = source;
    }

    @Override public long read(Buffer sink, long byteCount) {
      try (Buffer.UnsafeCursor ignored = sink.readAndWriteUnsafe(cursor)) {
        long oldSize = sink.size();
        int length = (int) Math.min(source.remaining(), Math.min(8192, byteCount));
        if (length == 0) return -1;
        cursor.expandBuffer(length);
        source.get(cursor.data, cursor.start, length);
        cursor.resizeBuffer(oldSize + length);
        return length;
      }
    }

    @Override public Timeout timeout() {
      return Timeout.NONE;
    }

    @Override public void close() {
    }
  }
}
