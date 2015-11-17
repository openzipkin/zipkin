/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import java.util.List;

import static java.util.Arrays.asList;

public final class TestObjects {

  public static final long WEB_SPAN_ID = -692101025335252320L;
  public static final long QUERY_SPAN_ID = -7842865617155193778L;
  public static final long JDBC_SPAN_ID = 8207293009014896295L;
  public static final Endpoint WEB_ENDPOINT = Endpoint.create("zipkin-web", 172 << 24 | 17 << 16 | 3, 8080);
  public static final Endpoint QUERY_ENDPOINT = Endpoint.create("zipkin-query", 172 << 24 | 17 << 16 | 2, 9411);
  public static final Endpoint JDBC_ENDPOINT = Endpoint.create("zipkin-jdbc", 172 << 24 | 17 << 16 | 2);

  public static final List<Span> TRACE = asList(
      new Span.Builder() // browser calls web
          .traceId(WEB_SPAN_ID)
          .name("get")
          .id(WEB_SPAN_ID)
          .timestamp(1444438900939000L)
          .duration(376000L)
          .addAnnotation(Annotation.create(1444438900939000L, Constants.SERVER_RECV, WEB_ENDPOINT))
          .addAnnotation(Annotation.create(1444438901315000L, Constants.SERVER_SEND, WEB_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, WEB_ENDPOINT))
          .build(),
      new Span.Builder() // web calls query
          .traceId(WEB_SPAN_ID)
          .name("get")
          .id(QUERY_SPAN_ID)
          .parentId(WEB_SPAN_ID)
          .timestamp(1444438900941000L)
          .duration(77000L)
          .addAnnotation(Annotation.create(1444438900941000L, Constants.CLIENT_SEND, WEB_ENDPOINT))
          .addAnnotation(Annotation.create(1444438900947000L, Constants.SERVER_RECV, QUERY_ENDPOINT))
          .addAnnotation(Annotation.create(1444438901017000L, Constants.SERVER_SEND, QUERY_ENDPOINT))
          .addAnnotation(Annotation.create(1444438901018000L, Constants.CLIENT_RECV, WEB_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, QUERY_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, WEB_ENDPOINT))
          .build(),
      new Span.Builder() // query calls jdbc
          .traceId(WEB_SPAN_ID)
          .name("query")
          .id(JDBC_SPAN_ID)
          .parentId(QUERY_SPAN_ID)
          .timestamp(1444438900948000L)
          .duration(31000L)
          .addAnnotation(Annotation.create(1444438900948000L, Constants.CLIENT_SEND, QUERY_ENDPOINT))
          .addAnnotation(Annotation.create(1444438900979000L, Constants.CLIENT_RECV, QUERY_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, JDBC_ENDPOINT))
          .build()
  );

}
