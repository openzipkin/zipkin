/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zipkin2.elasticsearch.ElasticsearchStorageTest.RESPONSE_UNAUTHORIZED;

class OpensearchVersionTest {
  static final OpensearchVersion V1_3 = new OpensearchVersion(1, 3);
  static final OpensearchVersion V2_11 = new OpensearchVersion(2, 11);

  static final AggregatedHttpResponse VERSION_RESPONSE_1 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "zipkin-elasticsearch",
        "cluster_name" : "docker-cluster",
        "cluster_uuid" : "wByRPgSgTryYl0TZXW4MsA",
        "version" : {
          "distribution" : "opensearch",
          "number" : "1.3.14",
          "build_type" : "tar",
          "build_hash" : "21940d8239b50285ef7f98a1762ef281a5b1c7ee",
          "build_date" : "2023-12-08T22:13:08.793451Z",
          "build_snapshot" : false,
          "lucene_version" : "8.10.1",
          "minimum_wire_compatibility_version" : "6.8.0",
          "minimum_index_compatibility_version" : "6.0.0-beta1"
        },
        "tagline" : "The OpenSearch Project: https://opensearch.org/"
      }
      """);
  static final AggregatedHttpResponse VERSION_RESPONSE_2 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "PV-NhJd",
        "cluster_name" : "CollectorDBCluster",
        "cluster_uuid" : "UjZaM0fQRC6tkHINCg9y8w",
         "version" : {
          "distribution" : "opensearch",
          "number" : "2.11.1",
          "build_type" : "tar",
          "build_hash" : "6b1986e964d440be9137eba1413015c31c5a7752",
          "build_date" : "2023-11-29T21:43:10.135035992Z",
          "build_snapshot" : false,
          "lucene_version" : "9.7.0",
          "minimum_wire_compatibility_version" : "7.10.0",
          "minimum_index_compatibility_version" : "7.0.0"
        },
        "tagline" : "The OpenSearch Project: https://opensearch.org/"
      }
      """);

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  @BeforeEach void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri())).build();
  }

  @AfterEach void tearDown() {
    storage.close();
  }

  ElasticsearchStorage storage;

  @Test void wrongContent() {
    server.enqueue(AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.OK),
      HttpData.ofUtf8("you got mail")));

    assertThatThrownBy(() -> OpensearchVersion.get(storage.http()))
      .hasMessage(".version.number not found in response: you got mail");
  }

  @Test void unauthorized() {
    server.enqueue(RESPONSE_UNAUTHORIZED);

    assertThatThrownBy(() -> ElasticsearchVersion.get(storage.http()))
      .hasMessage("User: anonymous is not authorized to perform: es:ESHttpGet");
  }

  @Test void version1() throws Exception {
    server.enqueue(VERSION_RESPONSE_1);

    assertThat(OpensearchVersion.get(storage.http()))
      .isEqualTo(V1_3);
  }

  /** Prove we compare better than a float. A float of 2.10 is the same as 2.1! */
  @Test void version2_10IsGreaterThan_V2_2() {
    assertThat(new OpensearchVersion(2, 10))
      .hasToString("2.10")
      .isGreaterThan(new OpensearchVersion(2, 2));
  }
}
