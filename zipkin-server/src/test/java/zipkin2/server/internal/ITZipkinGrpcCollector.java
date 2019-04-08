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
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.SpanServiceGrpc;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.config.name=zipkin-server",
    "zipkin.collector.grpc.enabled=true"
  })
@RunWith(SpringRunner.class)
public class ITZipkinGrpcCollector {
  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  @Test public void report_withGoogleGrpcLibrary() throws Exception {
    ListOfSpans message =
      ListOfSpans.parseFrom(SpanBytesEncoder.PROTO3.encodeList(TestObjects.TRACE));

    // sanity check the encoding is the same by spot checking a middle field
    for (int i = 0, length = TestObjects.TRACE.size(); i < length; i++) {
      assertThat(message.getSpans(i).getLocalEndpoint().getServiceName())
        .isEqualTo(TestObjects.TRACE.get(i).localServiceName());
    }

    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",
      server.activePort().get().localAddress().getPort()).usePlaintext().build();
    try {
      SpanServiceGrpc.newBlockingStub(channel).report(message);
    } finally {
      channel.shutdown();
    }

    assertThat(storage.getTraces())
      .containsExactly(TestObjects.TRACE);
  }
}
