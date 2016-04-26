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
package zipkin.scribe;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;
import zipkin.AsyncSpanConsumer;
import zipkin.Sampler;
import zipkin.StorageComponent;
import zipkin.internal.Lazy;
import zipkin.spanstore.guava.GuavaSpanConsumer;

import static java.util.Collections.emptyList;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link GuavaSpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeCollector implements AutoCloseable {

  /** Configuration including defaults needed to receive spans from a Scribe category. */
  public static final class Builder {
    String category = "zipkin";
    int port = 9410;

    /** Category zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder category(String category) {
      this.category = checkNotNull(category, "category");
      return this;
    }

    /** The port to listen on. Defaults to 9410 */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public ScribeCollector writeTo(StorageComponent storage, Sampler sampler) {
      checkNotNull(storage, "storage");
      checkNotNull(sampler, "sampler");
      return new ScribeCollector(this, new Lazy<AsyncSpanConsumer>() {
        @Override protected AsyncSpanConsumer compute() {
          return checkNotNull(storage.asyncSpanConsumer(sampler), storage + ".asyncSpanConsumer()");
        }
      });
    }
  }

  final ThriftServer server;

  ScribeCollector(Builder builder, Lazy<AsyncSpanConsumer> consumer) {
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(builder.category, consumer);
    ThriftServiceProcessor processor =
        new ThriftServiceProcessor(new ThriftCodecManager(), emptyList(), scribe);
    server = new ThriftServer(processor, new ThriftServerConfig().setPort(builder.port)).start();
  }

  @Override
  public void close() {
    server.close();
  }
}
