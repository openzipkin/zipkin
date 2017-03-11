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
package zipkin.elasticsearch.http;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.SocketUtils;
import zipkin.server.ZipkinServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = ZipkinServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "zipkin.storage.type=elasticsearch",
        "zipkin.storage.elasticsearch.hosts=http://localhost:${mock.elasticsearch.port}",
        "zipkin.collector.scribe.enabled=false",
        "spring.config.name=zipkin-server"
    })
@ContextConfiguration(initializers = ZipkinServerTest.RandomPortInitializer.class)
public class ZipkinServerTest {
  OkHttpClient client = new OkHttpClient();

  @LocalServerPort
  int zipkinPort;

  @Autowired
  @Value("${mock.elasticsearch.port}")
  int elasticsearchPort;

  @Test
  public void connectsToConfiguredBackend() throws Exception {
    try (MockWebServer es = new MockWebServer()) {
      es.start(elasticsearchPort);
      es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"2.4.0\"}}"));
      es.enqueue(new MockResponse()); // template
      es.enqueue(new MockResponse()); // search (will fail because no content, but that's ok)

      client.newCall(
          new Request.Builder().url(
              HttpUrl.parse("http://localhost:" + zipkinPort + "/api/v1/services"))
              .get()
              .build()).execute();

      assertEquals("/", es.takeRequest().getPath()); // version
      assertEquals("/_template/zipkin_template", es.takeRequest().getPath());
      assertTrue(es.takeRequest().getPath().replaceAll("\\?.*", "").endsWith("/span/_search"));
    }
  }

  // approach borrowed from stackdriver-zipkin
  public static class RandomPortInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      int randomPort = SocketUtils.findAvailableTcpPort();
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
          "mock.elasticsearch.port=" + randomPort);
    }
  }
}
