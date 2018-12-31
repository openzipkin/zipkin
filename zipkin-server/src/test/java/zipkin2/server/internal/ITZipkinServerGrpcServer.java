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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import zipkin2.TestObjects;
import zipkin2.proto3.Annotation;
import zipkin2.proto3.Endpoint;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.PutSpansResponse;
import zipkin2.proto3.Span;
import zipkin2.proto3.SpanServiceGrpc;
import zipkin2.storage.InMemoryStorage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class ITZipkinServerGrpcServer {

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

  @Autowired InMemoryStorage storage;
  @Value("${zipkin.collector.grpc.port}") int grpcPort;

  private TestHelper testHelper = mock(TestHelper.class);

  @Test public void testUnary() throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
    SpanServiceGrpc.SpanServiceStub spanService = SpanServiceGrpc.newStub(channel);
    final CountDownLatch finishLatch = new CountDownLatch(1);

    ListOfSpans.Builder listOfSpans = ListOfSpans.newBuilder();
    for (int i = 0; i < 10; ++i) {
      listOfSpans.addSpans(PROTO_SPAN);
    }
    spanService.putSpans(listOfSpans.build(), new StreamObserver<PutSpansResponse>() {
      @Override
      public void onNext(PutSpansResponse spans) {
        testHelper.onMessage(spans);
        finishLatch.countDown();
      }

      @Override
      public void onError(Throwable t) {
        testHelper.onRpcError(t);
        finishLatch.countDown();
      }

      @Override
      public void onCompleted() {
        finishLatch.countDown();
      }
    });

    if (!finishLatch.await(10, TimeUnit.SECONDS)) {
      warning("grpc did not finish within 10 secs");
    }

    verify(testHelper, never()).onRpcError(any(Throwable.class));
    verify(testHelper).onMessage(PutSpansResponse.newBuilder().build());

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
