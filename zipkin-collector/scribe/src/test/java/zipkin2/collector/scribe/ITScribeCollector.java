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
package zipkin2.collector.scribe;

import java.util.List;
import org.jboss.netty.channel.ChannelException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.CheckResult;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.libthrift.LibthriftSender;
import zipkin2.storage.InMemoryStorage;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TRACE;

public class ITScribeCollector {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void consumesSpans() throws Exception {
    List<byte[]> encodedSpans =
      TRACE.stream().map(SpanBytesEncoder.THRIFT::encode).collect(toList());

    try (ScribeCollector server = ScribeCollector.newBuilder().storage(storage).port(12345).build();
         LibthriftSender client = LibthriftSender.newBuilder().host("127.0.0.1").port(12345).build()
    ) {
      server.start();
      client.sendSpans(encodedSpans).execute();
    }

    assertThat(storage.getTraces()).containsExactly(TRACE);
  }

  @Test public void check_failsWhenNotStarted() {
    try (ScribeCollector scribe =
           ScribeCollector.newBuilder().storage(storage).port(12345).build()) {

      CheckResult result = scribe.check();
      assertThat(result.ok()).isFalse();
      assertThat(result.error()).isInstanceOf(IllegalStateException.class);

      scribe.start();
      assertThat(scribe.check().ok()).isTrue();
    }
  }

  @Test public void start_failsWhenCantBindPort() {
    thrown.expect(ChannelException.class);
    thrown.expectMessage("Failed to bind to: 0.0.0.0/0.0.0.0:12345");

    ScribeCollector.Builder builder = ScribeCollector.newBuilder().storage(storage).port(12345);

    try (ScribeCollector first = builder.build().start()) {
      try (ScribeCollector samePort = builder.build().start()) {
      }
    }
  }
}
