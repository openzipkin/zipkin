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

package zipkin.autoconfigure.metrics;

import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;
import io.prometheus.client.spring.web.EnablePrometheusTiming;
import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnablePrometheusEndpoint
@EnableSpringBootMetricsCollector
@EnablePrometheusTiming
@Configuration
public class PrometheusMetricsAutoConfiguration {
  PrometheusMetricsAutoConfiguration() {
    DefaultExports.initialize();
  }

  // Obviates the state bug in MetricsFilter which implicitly registers and hides something you
  // can't create twice
  static final Histogram http_request_duration_seconds = Histogram.build()
    .labelNames("path", "method")
    .help("Response time histogram")
    .name("http_request_duration_seconds")
    .register();

  @Bean("http_request_duration_seconds") Histogram http_request_duration_seconds() {
    return http_request_duration_seconds;
  }

  @Bean public Filter prometheusMetricsFilter() {
    return new PrometheusDurationFilter();
  }

  /**
   * The normal prometheus metrics filter implicitly registers a histogram which is hidden in a
   * field and not deregistered on destroy. A registration of any second instance of that filter
   * fails trying to re-register the same collector by design (by brian-brazil). The rationale is
   * that you are not supposed to recreate the same histogram. However, this design prevents us from
   * doing that. brian-bazil's hard stance on this makes the filter unusable for applications who
   * run tests.
   *
   * <p>This filter replaces the normal prometheus filter, correcting the design flaw by allowing us
   * to re-use the JVM singleton. It also corrects a major flaw in the upstream filter which results
   * in double-counting of requests when they are performed asynchronously. When the culture changes
   * in the prometheus project such that bugs are fixable, please submit this so that it can help
   * others. For more info, see "brian's bomb" https://github.com/openzipkin/zipkin/issues/1811
   */
  static final class PrometheusDurationFilter implements Filter {
    @Override public void init(FilterConfig filterConfig) {
    }

    /**
     * Note that upstream also has a problem which is that it doesn't handle async properly.
     * MetricsFilter results in double-counting, which this implementation avoids.
     */
    @Override public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
      FilterChain filterChain) throws IOException, ServletException {

      HttpServletRequest request = (HttpServletRequest) servletRequest;

      // async servlets will enter the filter twice
      if (request.getAttribute("PrometheusDurationFilter") != null) {
        filterChain.doFilter(request, servletResponse);
        return;
      }

      request.setAttribute("PrometheusDurationFilter", "true");

      Histogram.Timer timer = http_request_duration_seconds
        .labels(request.getRequestURI(), request.getMethod())
        .startTimer();

      try {
        filterChain.doFilter(servletRequest, servletResponse);
      } finally {
        if (request.isAsyncStarted()) { // we don't have the actual response, handle later
          request.getAsyncContext().addListener(new CompleteTimer(timer));
        } else { // we have a synchronous response, so we can finish the recording
          timer.observeDuration();
        }
      }
    }

    @Override public void destroy() {
    }
  }

  /** Inspired by WingtipsRequestSpanCompletionAsyncListener */
  static final class CompleteTimer implements AsyncListener {
    final Histogram.Timer timer;
    volatile boolean completed = false;

    CompleteTimer(Histogram.Timer timer) {
      this.timer = timer;
    }

    @Override public void onComplete(AsyncEvent e) {
      tryComplete();
    }

    @Override public void onTimeout(AsyncEvent e) {
      tryComplete();
    }

    @Override public void onError(AsyncEvent e) {
      tryComplete();
    }

    /** Only observes the first completion event */
    void tryComplete() {
      if (completed) return;
      timer.observeDuration();
      completed = true;
    }

    /** If another async is created (ex via asyncContext.dispatch), this needs to be re-attached */
    @Override public void onStartAsync(AsyncEvent event) {
      AsyncContext eventAsyncContext = event.getAsyncContext();
      if (eventAsyncContext != null) eventAsyncContext.addListener(this);
    }
  }
}
