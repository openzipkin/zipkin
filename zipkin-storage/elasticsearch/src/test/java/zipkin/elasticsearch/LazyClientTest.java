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

import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class LazyClientTest {

  @Test
  public void testToString() {
    LazyClient lazyClient = new LazyClient(new ElasticsearchStorage.Builder()
        .cluster("cluster")
        .hosts(asList("host1", "host2")));

    assertThat(lazyClient)
        .hasToString("{\"clusterName\": \"cluster\", \"hosts\": [\"host1\", \"host2\"]}");
  }
}
