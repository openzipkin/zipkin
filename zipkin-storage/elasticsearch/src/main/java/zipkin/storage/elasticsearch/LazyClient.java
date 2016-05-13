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
package zipkin.storage.elasticsearch;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import zipkin.internal.LazyCloseable;

final class LazyClient extends LazyCloseable<Client> {
  private final String clusterName;
  private final List<String> hosts;
  private final String indexTemplate;

  LazyClient(ElasticsearchStorage.Builder builder) {
    this.clusterName = builder.cluster;
    this.hosts = builder.hosts;
    try {
      this.indexTemplate = Resources.toString(
          Resources.getResource("zipkin/storage/elasticsearch/zipkin_template.json"),
          StandardCharsets.UTF_8)
          .replace("${__INDEX__}", builder.index);
    } catch (IOException e) {
      throw new AssertionError("Error reading jar resource, shouldn't happen.", e);
    }
  }

  @Override protected Client compute() {
    Settings settings = Settings.builder()
        .put("cluster.name", clusterName)
        .put("lazyClient.transport.sniff", true)
        .build();

    TransportClient client = TransportClient.builder()
        .settings(settings)
        .build();
    for (String host : hosts) {
      HostAndPort hostAndPort = HostAndPort.fromString(host);
      try {
        client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
            hostAndPort.getHostText()), hostAndPort.getPort()));
      } catch (UnknownHostException e) {
        // Hosts may be down transiently, we should still try to connect. If all of them happen
        // to be down we will fail later when trying to use the client when checking the index
        // template.
        continue;
      }
    }
    checkForIndexTemplate(client, indexTemplate);
    return client;
  }

  static void checkForIndexTemplate(Client client, String indexTemplate) {
    GetIndexTemplatesResponse existingTemplates =
        client.admin().indices().getTemplates(new GetIndexTemplatesRequest("zipkin_template"))
            .actionGet();
    if (!existingTemplates.getIndexTemplates().isEmpty()) {
      return;
    }
    client.admin().indices().putTemplate(
        new PutIndexTemplateRequest("zipkin_template").source(indexTemplate)).actionGet();
  }

  @Override public String toString() {
    StringBuilder json = new StringBuilder("{\"clusterName\": \"").append(clusterName).append("\"");
    json.append(", \"hosts\": [\"").append(Joiner.on("\", \"").join(hosts)).append("\"]");
    return json.append("}").toString();
  }

  @Override
  public void close() {
    Client maybeNull = maybeNull();
    if (maybeNull != null) maybeNull.close();
  }
}
