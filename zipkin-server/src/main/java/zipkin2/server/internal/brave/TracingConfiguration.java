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
package zipkin2.server.internal.brave;

import brave.Tracing;
import brave.context.log4j2.ThreadContextScopeDecorator;
import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import brave.http.HttpTracing;
import brave.propagation.B3SinglePropagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalSpan;
import brave.sampler.BoundarySampler;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import com.linecorp.armeria.common.brave.RequestContextCurrentTraceContext;
import com.linecorp.armeria.server.brave.BraveService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.CollectorMetrics;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.Sender;
import zipkin2.server.internal.ConditionalOnSelfTracing;
import zipkin2.storage.StorageComponent;

@Configuration
@EnableConfigurationProperties(SelfTracingProperties.class)
@ConditionalOnSelfTracing
public class TracingConfiguration {
  /** Configuration for how to buffer spans into messages for Zipkin */
  @Bean Reporter<Span> reporter(BeanFactory factory, SelfTracingProperties config) {
    return AsyncReporter.builder(new LocalSender(factory))
      .threadFactory((runnable) -> new Thread(new Runnable() {
        @Override public void run() {
          RequestContextCurrentTraceContext.setCurrentThreadNotRequestThread(true);
          runnable.run();
        }

        @Override public String toString() {
          return runnable.toString();
        }
      }))
      .messageTimeout(config.getMessageTimeout().toNanos(), TimeUnit.NANOSECONDS)
      .metrics(new ReporterMetricsAdapter(factory))
      .build();
  }

  @Bean CurrentTraceContext currentTraceContext() {
    return RequestContextCurrentTraceContext.builder()
      .addScopeDecorator(ThreadContextScopeDecorator.create()) // puts trace IDs into logs
      .build();
  }

  /**
   * There's no attribute namespace shared across request and response. Hence, we need to save off a
   * reference to the span in scope, so that we can close it in the response.
   */
  @Bean ThreadLocalSpan threadLocalSpan(Tracing tracing) {
    return ThreadLocalSpan.create(tracing.tracer());
  }

  /**
   * This controls the general rate. In order to not accidentally start traces started from the
   * tracer itself, this isn't used as {@link Tracing.Builder#sampler(Sampler)}. The impact of this
   * is that we can't currently start traces from Kafka or Rabbit (until we use a messaging
   * sampler).
   *
   * See https://github.com/openzipkin/brave/pull/914 for the messaging abstraction
   */
  @Bean Sampler sampler(SelfTracingProperties config) {
    if (config.getSampleRate() != 1.0) {
      if (config.getSampleRate() < 0.01) {
        return BoundarySampler.create(config.getSampleRate());
      } else {
        return Sampler.create(config.getSampleRate());
      }
    } else if (config.getTracesPerSecond() != 0) {
      return RateLimitingSampler.create(config.getTracesPerSecond());
    }
    return Sampler.ALWAYS_SAMPLE;
  }

  /** Controls aspects of tracing such as the name that shows up in the UI */
  @Bean Tracing tracing(Reporter<Span> reporter) {
    return Tracing.newBuilder()
      .localServiceName("zipkin-server")
      .sampler(Sampler.NEVER_SAMPLE) // don't sample traces at this abstraction
      .currentTraceContext(currentTraceContext())
      // Reduce the impact on untraced downstream http services such as Elasticsearch
      .propagationFactory(B3SinglePropagation.FACTORY)
      .spanReporter(reporter)
      .build();
  }

  @Bean HttpTracing httpTracing(Tracing tracing, Sampler sampler) {
    return HttpTracing.newBuilder(tracing)
      // server starts traces for read requests under the path /api
      .serverSampler(new HttpSampler() {
        @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
          String path = adapter.path(request);
          if (path.startsWith("/api") || path.startsWith("/zipkin/api")) {
            return sampler.isSampled(0L); // use the global rate limit
          }
          return false;
        }
      })
      .build();
  }

  @Bean ArmeriaServerConfigurator tracingConfigurator(HttpTracing tracing) {
    return server -> server.decorator(BraveService.newDecorator(tracing));
  }

  /**
   * Defined locally as StorageComponent is a lazy proxy, and we need to avoid eagerly calling it.
   */
  static final class LocalSender extends Sender {
    final BeanFactory factory;
    volatile StorageComponent delegate; // volatile to prevent stale reads

    LocalSender(BeanFactory factory) {
      this.factory = factory;
    }

    @Override public Encoding encoding() {
      return Encoding.PROTO3;
    }

    @Override public int messageMaxBytes() {
      return 5 * 1024 * 1024; // arbitrary
    }

    @Override public int messageSizeInBytes(List<byte[]> list) {
      return Encoding.PROTO3.listSizeInBytes(list);
    }

    @Override public Call<Void> sendSpans(List<byte[]> encodedSpans) {
      List<Span> spans = new ArrayList<>(encodedSpans.size());
      for (byte[] encodedSpan : encodedSpans) {
        Span v2Span = SpanBytesDecoder.PROTO3.decodeOne(encodedSpan);
        spans.add(v2Span);
      }
      return delegate().spanConsumer().accept(spans);
    }

    @Override public CheckResult check() {
      return delegate().check();
    }

    @Override public String toString() {
      // Avoid using the delegate to avoid eagerly loading the bean during initialization
      return "StorageComponent";
    }

    @Override public void close() {
      // don't close delegate as we didn't open it!
    }

    /** Lazy lookup to avoid proxying */
    StorageComponent delegate() {
      StorageComponent result = delegate;
      if (result != null) return delegate;
      // synchronization is not needed as redundant calls have no ill effects
      result = factory.getBean(StorageComponent.class);
      if (result instanceof TracingStorageComponent) {
        result = ((TracingStorageComponent) result).delegate;
      }
      return delegate = result;
    }
  }

  static final class ReporterMetricsAdapter implements ReporterMetrics {
    final BeanFactory factory;
    volatile CollectorMetrics delegate; // volatile to prevent stale reads

    ReporterMetricsAdapter(BeanFactory factory) {
      this.factory = factory;
    }

    @Override public void incrementMessages() {
      delegate().incrementMessages();
    }

    @Override public void incrementMessagesDropped(Throwable throwable) {
      delegate().incrementMessagesDropped();
    }

    @Override public void incrementSpans(int i) {
      delegate().incrementSpans(i);
    }

    @Override public void incrementSpanBytes(int i) {
      delegate().incrementBytes(i);
    }

    @Override public void incrementMessageBytes(int i) {
    }

    @Override public void incrementSpansDropped(int i) {
      delegate().incrementMessagesDropped();
    }

    @Override public void updateQueuedSpans(int i) {
    }

    @Override public void updateQueuedBytes(int i) {
    }

    /** Lazy lookup to avoid proxying */
    CollectorMetrics delegate() {
      CollectorMetrics result = delegate;
      if (result != null) return delegate;
      // synchronization is not needed as redundant calls have no ill effects
      return delegate = factory.getBean(CollectorMetrics.class).forTransport("local");
    }
  }
}
