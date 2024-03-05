/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.elasticsearch.internal.JsonReaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static zipkin2.elasticsearch.internal.JsonReaders.collectValuesNamed;
import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;

class JsonReadersTest {
  @Test void enterPath_nested() throws IOException {
    String content = """
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
      """;

    assertThat(
      JsonReaders.enterPath(JSON_FACTORY.createParser(content), "version", "number").getText())
      .isEqualTo("2.4.0");
  }

  @Test void enterPath_nullOnNoInput() throws IOException {
    assertThat(JsonReaders.enterPath(JSON_FACTORY.createParser(""), "message"))
      .isNull();
  }

  @Test void enterPath_nullOnWrongInput() throws IOException {
    assertThat(JsonReaders.enterPath(JSON_FACTORY.createParser("[]"), "message"))
      .isNull();
  }

  @Test void collectValuesNamed_emptyWhenNotFound() throws IOException {
    String content = """
      {
        "took": 1,
        "timed_out": false,
        "_shards": {
          "total": 0,
          "successful": 0,
          "failed": 0
        },
        "hits": {
          "total": 0,
          "max_score": 0,
          "hits": []
        }
      }
      """;

    assertThat(collectValuesNamed(JSON_FACTORY.createParser(content), "key")).isEmpty();
  }

  // All elasticsearch results start with an object, not an array.
  @Test void collectValuesNamed_exceptionOnWrongData() throws IOException {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      assertThat(collectValuesNamed(JSON_FACTORY.createParser("[]"), "key")).isEmpty();
    });
  }

  @Test void collectValuesNamed_mergesArrays() throws IOException {
    List<String> result =
      collectValuesNamed(JSON_FACTORY.createParser(TestResponses.SPAN_NAMES), "key");

    assertThat(result).containsExactly("methodcall", "yak");
  }

  @Test void collectValuesNamed_mergesChildren() throws IOException {
    List<String> result =
      collectValuesNamed(JSON_FACTORY.createParser(TestResponses.SERVICE_NAMES), "key");

    assertThat(result).containsExactly("yak", "service");
  }

  @Test void collectValuesNamed_nested() throws IOException {
    String content = """
      {
        "took": 49,
        "timed_out": false,
        "_shards": {
          "total": 5,
          "successful": 5,
          "failed": 0
        },
        "hits": {
          "total": 1,
          "max_score": 0,
          "hits": []
        },
        "aggregations": {
          "traceId_agg": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "000000000000007b",
                "doc_count": 1,
                "timestamps_agg": {
                  "value": 1474761600001,
                  "value_as_string": "1474761600001"
                }
              }
            ]
          }
        }
      }
      """;

    assertThat(collectValuesNamed(JSON_FACTORY.createParser(content), "key"))
      .containsExactly("000000000000007b");
  }
}
