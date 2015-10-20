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
package io.zipkin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import io.zipkin.Annotation;
import io.zipkin.Codec;
import io.zipkin.Endpoint;
import io.zipkin.Span;

@SpringApplicationConfiguration(classes = ZipkinServer.class)
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestPropertySource(properties = { "zipkin.store.type=mem",
    "spring.config.name=zipkin-server" })
public class ZipkinServerIntegrationTests {

  @Autowired
  ConfigurableWebApplicationContext context;
  private MockMvc mockMvc;

  @Before
  public void init() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
  }

  @Test
  public void addJsonSpan() throws Exception {
    byte[] body = Codec.JSON
        .writeSpans(Arrays.asList(newSpan(1L, 1L, "foo", "an", "bar")));
    this.mockMvc.perform(post("/api/v1/spans").content(body))
        .andExpect(status().isAccepted());
  }

  @Test
  public void addThriftSpan() throws Exception {
    byte[] body = Codec.THRIFT
        .writeSpans(Arrays.asList(newSpan(1L, 2L, "foo", "an", "bar")));
    this.mockMvc
        .perform(post("/api/v1/spans").content(body).contentType("application/x-thrift"))
        .andExpect(status().isAccepted());
  }

  private static Span newSpan(long traceId, long id, String spanName, String value,
      String service) {
    Endpoint endpoint = new Endpoint.Builder().serviceName(service).port((short) 80)
        .ipv4(0).build();
    Annotation annotation = new Annotation.Builder().endpoint(endpoint).value(value)
        .timestamp(System.currentTimeMillis()).build();
    Span span = new Span.Builder().id(id).traceId(traceId).name(spanName)
        .addAnnotation(annotation).build();
    return span;
  }
}
