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
package zipkin.junit;

import okhttp3.HttpUrl;
import org.junit.Test;
import zipkin.Constants;
import zipkin.storage.QueryRequest;
import zipkin.TraceKeys;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinDispatcherTest {

  HttpUrl baseUrl = HttpUrl.parse("http://localhost:9411/api/v1/traces");

  @Test
  public void toQueryRequest() {
    HttpUrl url = baseUrl.newBuilder()
        .addQueryParameter("serviceName", "zipkin-server")
        .addQueryParameter("spanName", "get")
        .addQueryParameter("limit", "1000").build();

    QueryRequest request = ZipkinDispatcher.toQueryRequest(url);

    assertThat(request.serviceName).isEqualTo("zipkin-server");
    assertThat(request.spanName).isEqualTo("get");
    assertThat(request.limit).isEqualTo(1000);
  }

  @Test
  public void toQueryRequest_parseAnnotations() {
    HttpUrl url = baseUrl.newBuilder()
        .addQueryParameter("serviceName", "zipkin-server")
        .addQueryParameter("annotationQuery", "error and finagle.timeout").build();

    QueryRequest request = ZipkinDispatcher.toQueryRequest(url);

    assertThat(request.annotations)
        .containsExactly(Constants.ERROR, "finagle.timeout");
  }

  @Test
  public void toQueryRequest_parseBinaryAnnotations() {
    HttpUrl url = baseUrl.newBuilder()
        .addQueryParameter("serviceName", "myService")
        .addQueryParameter("annotationQuery", "http.status_code=500").build();

    QueryRequest request = ZipkinDispatcher.toQueryRequest(url);

    assertThat(request.binaryAnnotations)
        .hasSize(1)
        .containsEntry(TraceKeys.HTTP_STATUS_CODE, "500");
  }

  @Test
  public void toQueryRequest_parseBinaryAnnotations_withSlash() {
    HttpUrl url = baseUrl.newBuilder()
        .addQueryParameter("serviceName", "myService")
        .addQueryParameter("annotationQuery", "http.path=/sessions").build();

    QueryRequest request = ZipkinDispatcher.toQueryRequest(url);

    assertThat(request.binaryAnnotations)
        .hasSize(1)
        .containsEntry(TraceKeys.HTTP_PATH, "/sessions");
  }
}
