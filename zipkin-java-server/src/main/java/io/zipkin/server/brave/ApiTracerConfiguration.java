/**
 * Copyright 2015 The OpenZipkin Authors
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
// TODO: switch package back after https://github.com/openzipkin/brave/pull/99
package io.zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.spring.ServletHandlerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
public class ApiTracerConfiguration extends WebMvcConfigurerAdapter {

  @Autowired
  Brave brave;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    ServerTracer tracer = brave.serverTracer();
    registry.addInterceptor(new ServletHandlerInterceptor(
        new ServerRequestInterceptor(tracer), new ServerResponseInterceptor(tracer),
        new DefaultSpanNameProvider(), brave.serverSpanThreadBinder()));
  }
}
