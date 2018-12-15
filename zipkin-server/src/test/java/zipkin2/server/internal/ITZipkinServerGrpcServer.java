/*
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
package zipkin2.server.internal;

import static junit.framework.TestSuite.warning;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.TestObjects;
import zipkin2.proto3.Annotation;
import zipkin2.proto3.Endpoint;
import zipkin2.proto3.PublishSpansResponse;
import zipkin2.proto3.Span;
import zipkin2.proto3.SpanServiceGrpc;
import zipkin2.storage.InMemoryStorage;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "zipkin.storage.type=", // cheat and test empty storage type
    "spring.config.name=zipkin-server",
    "zipkin.collector.grpc.enabled=true"
  })
@RunWith(SpringRunner.class)
public class ITZipkinServerGrpcServer {

  static final Span PROTO_SPAN = Span.newBuilder()
    .setTraceId(decodeHex(TestObjects.CLIENT_SPAN.traceId()))
    .setParentId(decodeHex(TestObjects.CLIENT_SPAN.parentId()))
    .setId(decodeHex(TestObjects.CLIENT_SPAN.id()))
    .setKind(Span.Kind.valueOf(TestObjects.CLIENT_SPAN.kind().name()))
    .setName(TestObjects.CLIENT_SPAN.name())
    .setTimestamp(TestObjects.CLIENT_SPAN.timestampAsLong())
    .setDuration(TestObjects.CLIENT_SPAN.durationAsLong())
    .setLocalEndpoint(Endpoint.newBuilder()
      .setServiceName(TestObjects.FRONTEND.serviceName())
      .setIpv4(ByteString.copyFrom(TestObjects.FRONTEND.ipv4Bytes()))
    )
    .setRemoteEndpoint(Endpoint.newBuilder()
      .setServiceName(TestObjects.BACKEND.serviceName())
      .setIpv4(ByteString.copyFrom(TestObjects.BACKEND.ipv4Bytes()))
      .setPort(TestObjects.BACKEND.portAsInt()).build()
    )
    .addAnnotations(Annotation.newBuilder()
      .setTimestamp(TestObjects.CLIENT_SPAN.annotations().get(0).timestamp())
      .setValue(TestObjects.CLIENT_SPAN.annotations().get(0).value())
      .build())
    .putAllTags(TestObjects.CLIENT_SPAN.tags())
    .setShared(true)
    .build();

  private Random random = new Random();

  @Autowired InMemoryStorage storage;
  @Value("${zipkin.collector.grpc.port}") int grpcPort;

  private TestHelper testHelper = mock(TestHelper.class);

  @Test public void writeSpans() throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
    SpanServiceGrpc.SpanServiceStub spanService = SpanServiceGrpc.newStub(channel);
    final CountDownLatch finishLatch = new CountDownLatch(1);
    final StreamObserver<Span> requestObserver = spanService.publishSpans(new StreamObserver<PublishSpansResponse>() {
      @Override
      public void onNext(PublishSpansResponse publishSpansResponse) {
        testHelper.onMessage(publishSpansResponse);
        finishLatch.countDown();
      }

      @Override
      public void onError(Throwable throwable) {
        testHelper.onRpcError(throwable);
        finishLatch.countDown();
      }

      @Override
      public void onCompleted() {
        finishLatch.countDown();
      }
    });

    try {
      for (int i = 0; i < 10; ++i) {
        requestObserver.onNext(PROTO_SPAN);
        Thread.sleep(random.nextInt(1000) + 500);
      }
    } catch (RuntimeException e) {
      requestObserver.onError(e);
      throw e;
    }

    requestObserver.onCompleted();

    if (!finishLatch.await(10, TimeUnit.SECONDS)) {
      warning("grpc did not finish within 10 secs");
    }

    verify(testHelper).onMessage(PublishSpansResponse.newBuilder().build());
    verify(testHelper, never()).onRpcError(any(Throwable.class));

    assertThat(storage.acceptedSpanCount()).isEqualTo(10);
    for (zipkin2.Span received : storage.getTraces().get(0)) {
      assertThat(received.traceId()).isEqualTo(TestObjects.CLIENT_SPAN.traceId());
      assertThat(received.parentId()).isEqualTo(TestObjects.CLIENT_SPAN.parentId());
      assertThat(received.id()).isEqualTo(TestObjects.CLIENT_SPAN.id());
    }

    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  interface TestHelper {
    /**
     * Used for verify/inspect message received from server.
     */
    void onMessage(Message message);

    /**
     * Used for verify/inspect error received from server.
     */
    void onRpcError(Throwable exception);
  }

  static ByteString decodeHex(String s) {
    return ByteString.copyFrom(BaseEncoding.base16().lowerCase().decode(s));
  }

}
