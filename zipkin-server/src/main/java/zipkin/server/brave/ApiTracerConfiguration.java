/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.spring.ServletHandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
public class ApiTracerConfiguration extends WebMvcConfigurerAdapter {

  @Autowired
  Brave brave;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    ServerTracer tracer = brave.serverTracer();
    ServletHandlerInterceptor traceInterceptor = new ServletHandlerInterceptor(
        new ServerRequestInterceptor(tracer), new ServerResponseInterceptor(tracer),
        new DefaultSpanNameProvider(), brave.serverSpanThreadBinder());
    registry.addInterceptor(new NoPOSTHandlerInterceptorAdapter(traceInterceptor));
  }

  static class NoPOSTHandlerInterceptorAdapter implements AsyncHandlerInterceptor {
    private final AsyncHandlerInterceptor delegate;

    NoPOSTHandlerInterceptorAdapter(AsyncHandlerInterceptor delegate) {
      this.delegate = delegate;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object o) throws Exception {
      if (!request.getMethod().equals("POST")) {
        delegate.afterConcurrentHandlingStarted(request, response, o);
      }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) throws Exception {
      return request.getMethod().equals("POST") || delegate.preHandle(request, response, o);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object o, ModelAndView modelAndView) throws Exception {
      if (!request.getMethod().equals("POST")) {
        delegate.postHandle(request, response, o, modelAndView);
      }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object o, Exception e) throws Exception {
      if (!request.getMethod().equals("POST")) {
        delegate.afterCompletion(request, response, o, e);
      }
    }
  }
}
