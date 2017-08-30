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

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import zipkin.server.brave.TracedStorageComponent;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ZipkinServer.class, properties = {
    "zipkin.storage.type=mem",
    "spring.config.name=zipkin-server",
    "zipkin.self-tracing.enabled=true"
})
@RunWith(SpringRunner.class)
public class ZipkinServerSelfTracingTest {

  @Autowired
  ConfigurableWebApplicationContext context;

  @Test
  @Ignore // TODO: be able to self-trace V2StorageComponent
  public void selfTraceStorageComponent() throws Exception {
    assertThat(context.getBean(StorageComponent.class))
        .isInstanceOf(TracedStorageComponent.class);
  }
}
