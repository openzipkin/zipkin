/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal.elasticsearch;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class HostsConverter {
  static final Logger LOGGER = LogManager.getLogger();

  static List<URI> convert(String hosts) {
    if (hosts == null) return Collections.singletonList(URI.create("http://localhost:9200"));
    return Stream.of(hosts.split(",", 100)).map(host -> {
      if (host.startsWith("http://") || host.startsWith("https://")) {
        return URI.create(host);
      }
      URI result = URI.create("http://" + host);
      if (result.getPort() == -1) {
        return URI.create("http://" + host + ":9200");
      } else if (result.getPort() == 9300) {
        LOGGER.warn("Native transport no longer supported. Changing {} to http port 9200", host);
        return URI.create("http://" + host.replace(":9300", ":9200"));
      }
      return result;
    }).collect(Collectors.toList());
  }
}
