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
import static zipkin2.elasticsearch.ElasticsearchVersion.V5_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_0;

class ElasticsearchVersionTest {
  static final ElasticsearchVersion V2_4 = new ElasticsearchVersion(2, 4);
  static final ElasticsearchVersion V6_7 = new ElasticsearchVersion(6, 7);

  static final AggregatedHttpResponse VERSION_RESPONSE_7 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "zipkin-elasticsearch",
        "cluster_name" : "docker-cluster",
        "cluster_uuid" : "wByRPgSgTryYl0TZXW4MsA",
        "version" : {
          "number" : "7.0.1",
          "build_flavor" : "default",
          "build_type" : "tar",
          "build_hash" : "e4efcb5",
          "build_date" : "2019-04-29T12:56:03.145736Z",
          "build_snapshot" : false,
          "lucene_version" : "8.0.0",
          "minimum_wire_compatibility_version" : "6.7.0",
          "minimum_index_compatibility_version" : "6.0.0-beta1"
        },
        "tagline" : "You Know, for Search"
      }
      """);
  static final AggregatedHttpResponse VERSION_RESPONSE_6 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "PV-NhJd",
        "cluster_name" : "CollectorDBCluster",
        "cluster_uuid" : "UjZaM0fQRC6tkHINCg9y8w",
        "version" : {
          "number" : "6.7.0",
          "build_flavor" : "oss",
          "build_type" : "tar",
          "build_hash" : "8453f77",
          "build_date" : "2019-03-21T15:32:29.844721Z",
          "build_snapshot" : false,
          "lucene_version" : "7.7.0",
          "minimum_wire_compatibility_version" : "5.6.0",
          "minimum_index_compatibility_version" : "5.0.0"
        },
        "tagline" : "You Know, for Search"
      }
      """);
  static final AggregatedHttpResponse VERSION_RESPONSE_5 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "vU0g1--",
        "cluster_name" : "elasticsearch",
        "cluster_uuid" : "Fnm277ITSNyzsy0UCVFN7g",
        "version" : {
          "number" : "5.0.0",
          "build_hash" : "253032b",
          "build_date" : "2016-10-26T04:37:51.531Z",
          "build_snapshot" : false,
          "lucene_version" : "6.2.0"
        },
        "tagline" : "You Know, for Search"
      }
      """);
  static final AggregatedHttpResponse VERSION_RESPONSE_2 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, """
      {
        "name" : "Kamal",
        "cluster_name" : "elasticsearch",
        "version" : {
          "number" : "2.4.0",
          "build_hash" : "ce9f0c7394dee074091dd1bc4e9469251181fc55",
          "build_timestamp" : "2016-08-29T09:14:17Z",
          "build_snapshot" : false,
          "lucene_version" : "5.5.2"
        },
        "tagline" : "You Know, for Search"
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

    assertThatThrownBy(() -> ElasticsearchVersion.get(storage.http()))
      .hasMessage(".version.number not found in response: you got mail");
  }

  @Test void unauthorized() {
    server.enqueue(RESPONSE_UNAUTHORIZED);

    assertThatThrownBy(() -> ElasticsearchVersion.get(storage.http()))
      .hasMessage("User: anonymous is not authorized to perform: es:ESHttpGet");
  }

  /** Unsupported, but we should test that parsing works */
  @Test void version2() throws Exception {
    server.enqueue(VERSION_RESPONSE_2);

    assertThat(ElasticsearchVersion.get(storage.http()))
      .isEqualTo(V2_4);
  }

  @Test void version5() throws Exception {
    server.enqueue(VERSION_RESPONSE_5);

    assertThat(ElasticsearchVersion.get(storage.http()))
      .isEqualTo(V5_0);
  }

  @Test void version6() throws Exception {
    server.enqueue(VERSION_RESPONSE_6);

    assertThat(ElasticsearchVersion.get(storage.http()))
      .isEqualTo(V6_7);
  }

  @Test void version7() throws Exception {
    server.enqueue(VERSION_RESPONSE_7);

    assertThat(ElasticsearchVersion.get(storage.http()))
      .isEqualTo(V7_0);
  }

  /** Prove we compare better than a float. A float of 7.10 is the same as 7.1! */
  @Test void version7_10IsGreaterThan_V7_2() {
    assertThat(new ElasticsearchVersion(7, 10))
      .hasToString("7.10")
      .isGreaterThan(new ElasticsearchVersion(7, 2));
  }
}
