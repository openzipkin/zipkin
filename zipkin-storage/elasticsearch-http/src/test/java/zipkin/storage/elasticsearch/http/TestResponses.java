/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch.http;

public final class TestResponses {
  public static final String SERVICE_NAMES = "{\n"
      + "  \"took\": 4,\n"
      + "  \"timed_out\": false,\n"
      + "  \"_shards\": {\n"
      + "    \"total\": 5,\n"
      + "    \"successful\": 5,\n"
      + "    \"failed\": 0\n"
      + "  },\n"
      + "  \"hits\": {\n"
      + "    \"total\": 1,\n"
      + "    \"max_score\": 0,\n"
      + "    \"hits\": []\n"
      + "  },\n"
      + "  \"aggregations\": {\n"
      + "    \"binaryAnnotations_agg\": {\n"
      + "      \"doc_count\": 1,\n"
      + "      \"binaryAnnotationsServiceName_agg\": {\n"
      + "        \"doc_count_error_upper_bound\": 0,\n"
      + "        \"sum_other_doc_count\": 0,\n"
      + "        \"buckets\": [\n"
      + "          {\n"
      + "            \"key\": \"yak\",\n"
      + "            \"doc_count\": 1\n"
      + "          }\n"
      + "        ]\n"
      + "      }\n"
      + "    },\n"
      + "    \"annotations_agg\": {\n"
      + "      \"doc_count\": 2,\n"
      + "      \"annotationsServiceName_agg\": {\n"
      + "        \"doc_count_error_upper_bound\": 0,\n"
      + "        \"sum_other_doc_count\": 0,\n"
      + "        \"buckets\": [\n"
      + "          {\n"
      + "            \"key\": \"service\",\n"
      + "            \"doc_count\": 2\n"
      + "          }\n"
      + "        ]\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}";

  public static final String SPAN_NAMES = "{\n"
      + "  \"took\": 1,\n"
      + "  \"timed_out\": false,\n"
      + "  \"_shards\": {\n"
      + "    \"total\": 5,\n"
      + "    \"successful\": 5,\n"
      + "    \"failed\": 0\n"
      + "  },\n"
      + "  \"hits\": {\n"
      + "    \"total\": 2,\n"
      + "    \"max_score\": 0,\n"
      + "    \"hits\": []\n"
      + "  },\n"
      + "  \"aggregations\": {\n"
      + "    \"name_agg\": {\n"
      + "      \"doc_count_error_upper_bound\": 0,\n"
      + "      \"sum_other_doc_count\": 0,\n"
      + "      \"buckets\": [\n"
      + "        {\n"
      + "          \"key\": \"methodcall\",\n"
      + "          \"doc_count\": 1\n"
      + "        },\n"
      + "        {\n"
      + "          \"key\": \"yak\",\n"
      + "          \"doc_count\": 1\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  }\n"
      + "}";
}
