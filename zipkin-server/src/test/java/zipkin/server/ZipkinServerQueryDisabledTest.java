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
import zipkin.autoconfigure.ui.ZipkinUiAutoConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Collector-only builds should be able to disable the query (and indirectly the UI), so that
 * associated assets 404 vs throw exceptions.
 */
@SpringBootTest(classes = ZipkinServer.class, properties = {
    "zipkin.storage.type=mem",
    "spring.config.name=zipkin-server",
    "zipkin.query.enabled=false",
    "zipkin.ui.enabled=false"
})
@RunWith(SpringRunner.class)
public class ZipkinServerQueryDisabledTest {

  @Autowired ConfigurableWebApplicationContext context;

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void disabledQueryBean() throws Exception {
    context.getBean(ZipkinQueryApiV1.class);
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void disabledUiBean() throws Exception {
    context.getBean(ZipkinUiAutoConfiguration.class);
  }

  @Test public void queryRelatedEndpoints404() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    mockMvc.perform(get("/api/v1/traces"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/index.html"))
        .andExpect(status().isNotFound());

    // but other endpoints are ok
    mockMvc.perform(get("/health"))
        .andExpect(status().isOk());
  }
}
