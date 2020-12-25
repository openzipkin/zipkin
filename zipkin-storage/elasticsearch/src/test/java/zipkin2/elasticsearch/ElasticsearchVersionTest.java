/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"zipkin-elasticsearch\",\n"
      + "  \"cluster_name\" : \"docker-cluster\",\n"
      + "  \"cluster_uuid\" : \"wByRPgSgTryYl0TZXW4MsA\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"7.0.1\",\n"
      + "    \"build_flavor\" : \"default\",\n"
      + "    \"build_type\" : \"tar\",\n"
      + "    \"build_hash\" : \"e4efcb5\",\n"
      + "    \"build_date\" : \"2019-04-29T12:56:03.145736Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"8.0.0\",\n"
      + "    \"minimum_wire_compatibility_version\" : \"6.7.0\",\n"
      + "    \"minimum_index_compatibility_version\" : \"6.0.0-beta1\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_6 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"PV-NhJd\",\n"
      + "  \"cluster_name\" : \"CollectorDBCluster\",\n"
      + "  \"cluster_uuid\" : \"UjZaM0fQRC6tkHINCg9y8w\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"6.7.0\",\n"
      + "    \"build_flavor\" : \"oss\",\n"
      + "    \"build_type\" : \"tar\",\n"
      + "    \"build_hash\" : \"8453f77\",\n"
      + "    \"build_date\" : \"2019-03-21T15:32:29.844721Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"7.7.0\",\n"
      + "    \"minimum_wire_compatibility_version\" : \"5.6.0\",\n"
      + "    \"minimum_index_compatibility_version\" : \"5.0.0\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_5 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"vU0g1--\",\n"
      + "  \"cluster_name\" : \"elasticsearch\",\n"
      + "  \"cluster_uuid\" : \"Fnm277ITSNyzsy0UCVFN7g\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"5.0.0\",\n"
      + "    \"build_hash\" : \"253032b\",\n"
      + "    \"build_date\" : \"2016-10-26T04:37:51.531Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"6.2.0\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_2 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"Kamal\",\n"
      + "  \"cluster_name\" : \"elasticsearch\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"2.4.0\",\n"
      + "    \"build_hash\" : \"ce9f0c7394dee074091dd1bc4e9469251181fc55\",\n"
      + "    \"build_timestamp\" : \"2016-08-29T09:14:17Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"5.5.2\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");

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
