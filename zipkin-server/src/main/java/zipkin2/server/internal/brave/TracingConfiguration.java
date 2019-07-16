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
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalSpan;
import brave.sampler.BoundarySampler;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import com.linecorp.armeria.common.brave.RequestContextCurrentTraceContext;
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

  /** Controls aspects of tracing such as the name that shows up in the UI */
  @Bean Tracing tracing(Reporter<Span> reporter, SelfTracingProperties config) {
    final Sampler sampler;
    if (config.getSampleRate() != 1.0) {
      if (config.getSampleRate() < 0.01) {
        sampler = BoundarySampler.create(config.getSampleRate());
      } else {
        sampler = Sampler.create(config.getSampleRate());
      }
    } else if (config.getTracesPerSecond() != 0) {
      sampler = RateLimitingSampler.create(config.getTracesPerSecond());
    } else {
      sampler = Sampler.ALWAYS_SAMPLE;
    }
    return Tracing.newBuilder()
      .localServiceName("zipkin-server")
      .sampler(sampler)
      .currentTraceContext(currentTraceContext())
      .spanReporter(reporter)
      .build();
  }

  /**
   * Defined locally as StorageComponent is a lazy proxy, and we need to avoid eagerly calling it.
   */
  static final class LocalSender extends Sender {
    final BeanFactory factory;
    StorageComponent delegate;

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
      result = factory.getBean(StorageComponent.class);
      if (result instanceof TracingStorageComponent) {
        result = ((TracingStorageComponent) result).delegate;
      }
      return delegate = result;
    }
  }

  static final class ReporterMetricsAdapter implements ReporterMetrics {
    final BeanFactory factory;
    CollectorMetrics delegate;

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
      return delegate = factory.getBean(CollectorMetrics.class).forTransport("local");
    }
  }
}
