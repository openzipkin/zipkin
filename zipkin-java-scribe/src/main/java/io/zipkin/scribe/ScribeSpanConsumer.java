/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.scribe;

import com.facebook.swift.codec.ThriftCodec;
import com.facebook.swift.codec.ThriftCodecManager;
import io.zipkin.Span;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TMemoryBuffer;

public final class ScribeSpanConsumer implements Scribe {

  private final Consumer<Iterable<Span>> consumer;
  private final ThriftCodec<Span> spanCodec = new ThriftCodecManager().getCodec(Span.class);

  public ScribeSpanConsumer(Consumer<Iterable<Span>> consumer) {
    this.consumer = consumer;
  }

  @Override
  public ResultCode log(List<LogEntry> messages) {
    Stream<Span> spansToStore = messages.stream()
        .filter(m -> m.category().equals("zipkin"))
        .map(LogEntry::message)
        .map(m -> Base64.getMimeDecoder().decode(m)) // finagle-zipkin uses mime encoding
        .map(bytes -> {
          TMemoryBuffer transport = new TMemoryBuffer(bytes.length);
          try {
            transport.write(bytes);
            return spanCodec.read(new TBinaryProtocol(transport));
          } catch (Exception e) {
            return null;
          }
        })
        .filter(s -> s != null)
        .filter(s -> !(s.isClientSide() && s.serviceNames().contains("client")));
    consumer.accept(spansToStore.collect(Collectors.toList()));
    return ResultCode.OK;
  }
}
