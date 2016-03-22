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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("elasticsearch")
public class ZipkinElasticsearchProperties {

  /**
   * The elasticsearch cluster to connect to, defaults to "elasticsearch".
   */
  private String cluster = "elasticsearch";

  /**
   * A comma separated list of elasticsearch hostnodes to connect to, in host:port
   * format. The port should be the transport port, not the http port. Defaults to
   * "localhost:9300".
   */
  private String hosts = "localhost:9300";

  /**
   * The index prefix to use when generating daily index names. Defaults to zipkin.
   */
  private String index = "zipkin";

  public String getCluster() {
    return cluster;
  }

  public ZipkinElasticsearchProperties setCluster(String cluster) {
    this.cluster = cluster;
    return this;
  }

  public String getHosts() {
    return hosts;
  }

  public ZipkinElasticsearchProperties setHosts(String hosts) {
    this.hosts = hosts;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public ZipkinElasticsearchProperties setIndex(String index) {
    this.index = index;
    return this;
  }
}
