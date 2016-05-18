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
package zipkin.autoconfigure.collector.scribe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.collector.scribe.ScribeCollector;

@ConfigurationProperties("zipkin.collector.scribe")
public class ZipkinScribeCollectorProperties {
  private String category = "zipkin";
  private int port = 9410;

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public ScribeCollector.Builder toBuilder() {
    return ScribeCollector.builder()
        .category(category)
        .port(port);
  }
}
