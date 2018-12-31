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

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.PutSpansResponse;
import zipkin2.proto3.SpanServiceGrpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "zipkin.storage.type=", // cheat and test empty storage type
    "spring.config.name=zipkin-server",
  })
@RunWith(SpringRunner.class)
public class ITZipkinServerGrpcServerDisabled {

  @Value("${zipkin.collector.grpc.port}") int grpcPort;

  @Test public void serverIsNotStartedByDefault() throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
    SpanServiceGrpc.SpanServiceStub spanService = SpanServiceGrpc.newStub(channel);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> t = new AtomicReference<>();
    spanService.putSpans(ListOfSpans.getDefaultInstance(), new StreamObserver<PutSpansResponse>() {
      @Override
      public void onNext(PutSpansResponse value) {
        latch.countDown();
      }

      @Override
      public void onError(Throwable throwable) {
        t.set(throwable);
        latch.countDown();
      }

      @Override
      public void onCompleted() {

      }
    });

    latch.await(10, TimeUnit.SECONDS);
    assertThat(t.get()).isInstanceOf(StatusRuntimeException.class);
    StatusRuntimeException statusException = (StatusRuntimeException)t.get();
    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.UNAVAILABLE.getCode());
  }

}
