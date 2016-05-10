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
package zipkin.minimal;

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
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.server.ZipkinServer;

import static java.util.Arrays.asList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static zipkin.Constants.SERVER_RECV;

@SpringApplicationConfiguration(classes = ZipkinServer.class)
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestPropertySource(properties = {"zipkin.storage.type=mem", "spring.config.name=zipkin-server"})
public class ZipkinServerTest {

  @Autowired
  ConfigurableWebApplicationContext context;
  MockMvc mockMvc;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void readsBackSpanName() throws Exception {
    String service = "web";
    Endpoint endpoint = Endpoint.create(service, 127 << 24 | 1, 80);
    Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, endpoint);
    Span span = Span.builder().id(1L).traceId(1L).name("get").addAnnotation(ann).build();

    // write the span to the server
    mockMvc.perform(post("/api/v1/spans").content(Codec.JSON.writeSpans(asList(span))))
        .andExpect(status().isAccepted());

    // sleep as the the storage operation is async
    Thread.sleep(1000);

    // read back the span name, given its service
    mockMvc.perform(get("/api/v1/spans?serviceName=" + service))
        .andExpect(status().isOk())
        .andExpect(content().string("[\"" + span.name + "\"]"));
  }
}
