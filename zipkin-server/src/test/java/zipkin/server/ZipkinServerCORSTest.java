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
package zipkin.server;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for CORS configuration.
 *
 * Verifies that allowed-origins can be configured via properties (zipkin.query.allowed-origins).
 */
@SpringBootTest(classes = ZipkinServer.class)
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestPropertySource(properties = {"zipkin.storage.type=mem", "spring.config.name=zipkin-server", "zipkin.query.allowed-origins=foo.example.com"})
public class ZipkinServerCORSTest {

  @Autowired
  ConfigurableWebApplicationContext context;

  MockMvc mockMvc;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  public void shouldAllowConfiguredOrigin() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    mockMvc.perform(get("/api/v1/traces")
        .header(HttpHeaders.ORIGIN, "foo.example.com"))
           .andExpect(status().isOk());
  }

  @Test
  public void shouldDisallowOrigin() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    mockMvc.perform(get("/api/v1/traces")
        .header(HttpHeaders.ORIGIN, "bar.example.com"))
           .andExpect(status().isForbidden());
  }
}
