/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

final class TestResponses {
  static final String SPANS = """
    {
      "took": 4,
      "timed_out": false,
      "_shards": {
        "total": 5,
        "successful": 5,
        "skipped": 0,
        "failed": 0
      },
      "hits": {
        "total": 4,
        "max_score": 0,
        "hits": [
          {
            "_index": "zipkin:span-2019-07-20",
            "_type": "span",
            "_id": "7180c278b62e8f6a216a2aea45d08fc9-2a40476ca7a22f2c85ac18b9c1f3a99c",
            "_score": 0,
            "_source": {
              "traceId": "7180c278b62e8f6a216a2aea45d08fc9",
              "duration": 350000,
              "localEndpoint": {
                "serviceName": "frontend",
                "ipv4": "127.0.0.1"
              },
              "timestamp_millis": 1,
              "kind": "SERVER",
              "name": "get",
              "id": "0000000000000001",
              "timestamp": 1000
            }
          },
          {
            "_index": "zipkin:span-2019-07-20",
            "_type": "span",
            "_id": "7180c278b62e8f6a216a2aea45d08fc9-466fed1eb1d5cef4a76a227e83a7a7a8",
            "_score": 0,
            "_source": {
              "traceId": "7180c278b62e8f6a216a2aea45d08fc9",
              "duration": 200000,
              "remoteEndpoint": {
                "serviceName": "backend",
                "ipv4": "192.168.99.101",
                "port": 9000
              },
              "localEndpoint": {
                "serviceName": "frontend",
                "ipv4": "127.0.0.1"
              },
              "timestamp_millis": 51,
              "kind": "CLIENT",
              "name": "get",
              "annotations": [
                {
                  "timestamp": 101000,
                  "value": "foo"
                }
              ],
              "id": "0000000000000002",
              "parentId": "0000000000000001",
              "timestamp": 51000,
              "tags": {
                "clnt/finagle.version": "6.45.0",
                "http.path": "/api"
              }
            }
          },
          {
            "_index": "zipkin:span-2019-07-20",
            "_type": "span",
            "_id": "7180c278b62e8f6a216a2aea45d08fc9-74d915e86c8f53d59ef5850b4e966199",
            "_score": 0,
            "_source": {
              "traceId": "7180c278b62e8f6a216a2aea45d08fc9",
              "duration": 150000,
              "shared": true,
              "localEndpoint": {
                "serviceName": "backend",
                "ipv4": "192.168.99.101",
                "port": 9000
              },
              "timestamp_millis": 101,
              "kind": "SERVER",
              "name": "get",
              "id": "0000000000000002",
              "parentId": "0000000000000001",
              "timestamp": 101000
            }
          },
          {
            "_index": "zipkin:span-2019-07-20",
            "_type": "span",
            "_id": "7180c278b62e8f6a216a2aea45d08fc9-989c12147ff4ca03ce10d8488d93b89d",
            "_score": 0,
            "_source": {
              "traceId": "7180c278b62e8f6a216a2aea45d08fc9",
              "duration": 50000,
              "remoteEndpoint": {
                "serviceName": "db",
                "ipv6": "2001:db8::c001",
                "port": 3036
              },
              "localEndpoint": {
                "serviceName": "backend",
                "ipv4": "192.168.99.101",
                "port": 9000
              },
              "timestamp_millis": 151,
              "kind": "CLIENT",
              "name": "query",
              "annotations": [
                {
                  "timestamp": 191000,
                  "value": "⻩"
                }
              ],
              "id": "0000000000000003",
              "parentId": "0000000000000002",
              "timestamp": 151000,
              "tags": {
                  "error": "💩"
              }
            }
          }
        ]
      }
    }
    """;
  static final String SERVICE_NAMES =
    """
    {
      "took": 4,
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
        "binaryAnnotations_agg": {
          "doc_count": 1,
          "binaryAnnotationsServiceName_agg": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "yak",
                "doc_count": 1
              }
            ]
          }
        },
        "annotations_agg": {
          "doc_count": 2,
          "annotationsServiceName_agg": {
            "doc_count_error_upper_bound": 0,
            "sum_other_doc_count": 0,
            "buckets": [
              {
                "key": "service",
                "doc_count": 2
              }
            ]
          }
        }
      }
    }
    """;

  static final String SPAN_NAMES =
    """
    {
      "took": 1,
      "timed_out": false,
      "_shards": {
        "total": 5,
        "successful": 5,
        "failed": 0
      },
      "hits": {
        "total": 2,
        "max_score": 0,
        "hits": []
      },
      "aggregations": {
        "name_agg": {
          "doc_count_error_upper_bound": 0,
          "sum_other_doc_count": 0,
          "buckets": [
            {
              "key": "methodcall",
              "doc_count": 1
            },
            {
              "key": "yak",
              "doc_count": 1
            }
          ]
        }
      }
    }
    """;

  static final String AUTOCOMPLETE_VALUES = """
    {
      "took": 12,
      "timed_out": false,
      "_shards": {
        "total": 5,
        "successful": 5,
        "skipped": 0,
        "failed": 0
      },
      "hits": {
        "total": 2,
        "max_score": 0,
        "hits": [
          {
            "_index": "zipkin:autocomplete-2018-12-08",
            "_type": "autocomplete",
            "_id": "http.method|POST",
            "_score": 0
          },
          {
            "_index": "zipkin:autocomplete-2018-12-08",
            "_type": "autocomplete",
            "_id": "http.method|GET",
            "_score": 0
          }
        ]
      },
      "aggregations": {
        "tagValue": {
          "doc_count_error_upper_bound": 0,
          "sum_other_doc_count": 0,
          "buckets": [
            {
              "key": "get",
              "doc_count": 1
            },
            {
              "key": "post",
              "doc_count": 1
            }
          ]
        }
      }
    }
    """;
}
