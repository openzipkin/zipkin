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
package zipkin2.elasticsearch.internal.client;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;

import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * HTTP Request builder
 */
public class RequestBuilder {
  private final Request request;

  private RequestBuilder(String method, String endpoint) {
    this.request = new Request(method, endpoint);
  }

  public static RequestBuilder get(String... uri) {
    return new RequestBuilder("GET", endpoint(uri));
  }

  public static RequestBuilder put(String... uri) {
    return new RequestBuilder("PUT", endpoint(uri));
  }

  public static RequestBuilder post(String... uri) {
    return new RequestBuilder("POST", endpoint(uri));
  }

  public static RequestBuilder delete(String... uri) {
    return new RequestBuilder("DELETE", endpoint(uri));
  }

  public RequestBuilder parameter(String name, String value) {
    request.addParameter(name, value);
    return this;
  }

  public Set<String> parameterNames() {
    return request.getParameters().keySet();
  }

  public RequestBuilder header(String name, String value) {
    RequestOptions.Builder optionsBuilder = request.getOptions().toBuilder();
    optionsBuilder.addHeader(name, value);
    request.setOptions(optionsBuilder.build());
    return this;
  }

  public RequestBuilder tag(String tag) {
    return header("Tag", tag);
  }

  public RequestBuilder jsonEntity(String json) {
    request.setJsonEntity(json);
    return this;
  }

  private static String endpoint(String... uri) {
    String endpoint = Stream.of(uri).collect(Collectors.joining("/"));
    if (!endpoint.startsWith("/")) {
      endpoint = "/" + endpoint;
    }
    return endpoint;
  }

  public Request build() {
    return request;
  }

}
