/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.server.brave;

import brave.Tracer;
import brave.sampler.BoundarySampler;
import brave.sampler.Sampler;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.ServerClientAndLocalSpanState;
import com.github.kristofa.brave.TracerAdapter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.collector.CollectorMetrics;
import zipkin.internal.Nullable;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Reporter;
import zipkin.reporter.ReporterMetrics;
import zipkin.reporter.Sender;
import zipkin.server.ConditionalOnSelfTracing;
import zipkin.storage.StorageComponent;

import static zipkin.internal.Util.propagateIfFatal;

@Configuration
@ConditionalOnSelfTracing
@Import(ApiTracerConfiguration.class)
public class BraveConfiguration {

  /** This gets the lanIP without trying to lookup its name. */
  // http://stackoverflow.com/questions/8765578/get-local-ip-address-without-connecting-to-the-internet
  @Bean
  @Scope Endpoint local(@Value("${server.port:9411}") int port) {
    Endpoint.Builder builder = Endpoint.builder()
        .serviceName("zipkin-server")
        .port(port == -1 ? 0 : port);
    try {
      InetAddress address = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
          .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
          .filter(ip -> ip.isSiteLocalAddress())
          .findAny().get();
      builder.parseIp(address);
    } catch (Exception ignored) {
    }
    return builder.build();
  }

  // Note: there's a chicken or egg problem here. TracedStorageComponent wraps StorageComponent with
  // Brave. During initialization, if we eagerly reference StorageComponent from within Brave,
  // BraveTracedStorageComponentEnhancer won't be able to process it. TL;DR; if you take out Lazy
  // here, self-tracing will not affect the storage component, which reduces its effectiveness.
  @Bean Reporter<Span> reporter(@Lazy StorageComponent storage,
      @Value("${zipkin.self-tracing.flush-interval:1}") int flushInterval,
      CollectorMetrics metrics) {
    return AsyncReporter.builder(new LocalSender(storage))
        .messageTimeout(flushInterval, TimeUnit.SECONDS)
        .metrics(new ReporterMetricsAdapter(metrics.forTransport("local"))).build();
  }

  @Bean ServerClientAndLocalSpanState braveState(@Qualifier("local") Endpoint local) {
    com.twitter.zipkin.gen.Endpoint braveEndpoint = com.twitter.zipkin.gen.Endpoint.builder()
        .ipv4(local.ipv4)
        .ipv6(local.ipv6)
        .port(local.port)
        .serviceName(local.serviceName)
        .build();
    return new InheritableServerClientAndLocalSpanState(braveEndpoint);
  }

  @Bean Tracer braveTracer(Reporter<Span> reporter, @Qualifier("local") Endpoint local,
      @Value("${zipkin.self-tracing.sample-rate:1.0}") float rate) {
    return Tracer.newBuilder()
        .localEndpoint(local)
        .sampler(rate < 0.01 ? BoundarySampler.create(rate) : Sampler.create(rate))
        .reporter(reporter)
        .build();
  }

  @Bean Brave brave(Tracer braveTracer, ServerClientAndLocalSpanState braveState) {
    return TracerAdapter.newBrave(braveTracer, braveState);
  }

  /**
   * Defined locally as StorageComponent is a lazy proxy, and we need to avoid eagerly calling it.
   */
  static final class LocalSender implements Sender {
    private final StorageComponent delegate;

    LocalSender(StorageComponent delegate) {
      this.delegate = delegate;
    }

    @Override public Encoding encoding() {
      return Encoding.THRIFT;
    }

    @Override public int messageMaxBytes() {
      return 5 * 1024 * 1024; // arbitrary
    }

    @Override public int messageSizeInBytes(List<byte[]> list) {
      return Encoding.THRIFT.listSizeInBytes(list);
    }

    @Override public void sendSpans(List<byte[]> encodedSpans, Callback callback) {
      try {
        List<Span> spans = new ArrayList<>(encodedSpans.size());
        for (byte[] encodedSpan : encodedSpans) {
          spans.add(Codec.THRIFT.readSpan(encodedSpan));
        }
        delegate.asyncSpanConsumer().accept(spans, new CallbackAdapter(callback));
      } catch (Throwable t) {
        propagateIfFatal(t);
        callback.onError(t);
      }
    }

    @Override public CheckResult check() {
      return CheckResult.OK;
    }

    @Override public void close() throws IOException {
    }
  }

  static final class CallbackAdapter implements zipkin.storage.Callback<Void> {
    final Callback delegate;

    CallbackAdapter(Callback delegate) {
      this.delegate = delegate;
    }

    @Override public void onSuccess(@Nullable Void aVoid) {
      delegate.onComplete();
    }

    @Override public void onError(Throwable throwable) {
      delegate.onError(throwable);
    }
  }

  static final class ReporterMetricsAdapter implements ReporterMetrics {
    final CollectorMetrics delegate;

    ReporterMetricsAdapter(CollectorMetrics delegate) {
      this.delegate = delegate;
    }

    @Override public void incrementMessages() {
      delegate.incrementMessages();
    }

    @Override public void incrementMessagesDropped(Throwable throwable) {
      delegate.incrementMessagesDropped();
    }

    @Override public void incrementSpans(int i) {
      delegate.incrementSpans(i);
    }

    @Override public void incrementSpanBytes(int i) {
      delegate.incrementBytes(i);
    }

    @Override public void incrementMessageBytes(int i) {
    }

    @Override public void incrementSpansDropped(int i) {
      delegate.incrementMessagesDropped();
    }

    @Override public void updateQueuedSpans(int i) {
    }

    @Override public void updateQueuedBytes(int i) {
    }
  }
}
