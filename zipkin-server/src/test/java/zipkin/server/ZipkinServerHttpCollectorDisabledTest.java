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
package zipkin.server;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Query-only builds should be able to disable the HTTP collector, so that
 * associated assets 404 instead of allowing creation of spans.
 */
@SpringBootTest(classes = ZipkinServer.class, properties = {
  "zipkin.storage.type=mem",
  "spring.config.name=zipkin-server",
  "zipkin.collector.http.enabled=false"
})
@RunWith(SpringRunner.class)
public class ZipkinServerHttpCollectorDisabledTest {

  @Autowired ConfigurableWebApplicationContext context;

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void disabledHttpCollectorBean() throws Exception {
    context.getBean(ZipkinHttpCollector.class);
  }

  @Test public void httpCollectorEndpointReturns405() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    mockMvc.perform(post("/api/v1/spans"))
      .andExpect(status().isMethodNotAllowed());
  }

  @Test public void getOnSpansEndpointReturnsOK() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    mockMvc.perform(get("/api/v1/spans?serviceName=unknown"))
      .andExpect(status().isOk());
  }
}
