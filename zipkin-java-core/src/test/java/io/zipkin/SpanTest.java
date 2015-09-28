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

import static io.zipkin.BinaryAnnotation.Type.STRING;
import static io.zipkin.Constants.CLIENT_SEND;
import static java.util.Arrays.asList;

public class SpanTest {

  Endpoint web = Endpoint.builder()
      .ipv4(0x7f000001 /* 127.0.0.1 */)
      .port((short) 8080)
      .serviceName("web").build();

  Span span = Span.builder()
      .traceId(123)
      .name("methodcall")
      .id(456)
      .annotations(asList(
          Annotation.builder().timestamp(1).value(CLIENT_SEND).endpoint(web).build(),
          Annotation.builder().timestamp(20).value(CLIENT_SEND).endpoint(web).build()))
      .binaryAnnotations(asList(BinaryAnnotation.builder()
          .key("http.uri").value("/foo".getBytes())
          .type(STRING).endpoint(web).build()))
      .build();
}
