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
import com.facebook.swift.service.ThriftServiceProcessor;
import zipkin.AsyncSpanConsumer;
import zipkin.spanstore.guava.GuavaSpanConsumer;

import static java.util.Collections.emptyList;
import static zipkin.internal.Util.checkNotNull;

/**
 * This transport accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link GuavaSpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeTransport implements AutoCloseable {
  final ThriftServer server;

  public ScribeTransport(ScribeConfig config, AsyncSpanConsumer consumer) {
    checkNotNull(config, "config");
    checkNotNull(consumer, "consumer");
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(consumer, config.category);
    ThriftServiceProcessor processor =
        new ThriftServiceProcessor(new ThriftCodecManager(), emptyList(), scribe);
    server = new ThriftServer(processor, config.forThriftServer()).start();
  }

  @Override
  public void close() {
    server.close();
  }
}
