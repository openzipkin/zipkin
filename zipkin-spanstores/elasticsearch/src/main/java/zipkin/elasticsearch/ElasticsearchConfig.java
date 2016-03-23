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
package zipkin.elasticsearch;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static zipkin.internal.Util.checkNotNull;

public class ElasticsearchConfig {

  public static final class Builder {

    private String cluster = "elasticsearch";
    private List<String> hosts = Collections.singletonList("localhost:9300");
    private String index = "zipkin";

    /**
     * The elasticsearch cluster to connect to, defaults to "elasticsearch".
     */
    public Builder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    /**
     * A comma separated list of elasticsearch hostnodes to connect to, in host:port format. The
     * port should be the transport port, not the http port. Defaults to "localhost:9300".
     */
    public Builder hosts(List<String> hosts) {
      this.hosts = hosts;
      return this;
    }

    /**
     * The index prefix to use when generating daily index names. Defaults to zipkin.
     */
    public Builder index(String index) {
      this.index = index;
      return this;
    }

    public ElasticsearchConfig build() {
      return new ElasticsearchConfig(this);
    }
  }

  final String clusterName;
  final List<String> hosts;
  final String index;
  final String indexTemplate;

  ElasticsearchConfig(Builder builder) {
    clusterName = checkNotNull(builder.cluster, "builder.cluster");
    hosts = checkNotNull(builder.hosts, "builder.hosts");
    index = checkNotNull(builder.index, "builder.index");

    try {
      indexTemplate = Resources.toString(
          Resources.getResource("zipkin/elasticsearch/zipkin_template.json"),
          StandardCharsets.UTF_8)
          .replace("${__INDEX__}", index);
    } catch (IOException e) {
      throw new AssertionError("Error reading jar resource, shouldn't happen.", e);
    }
  }
}
