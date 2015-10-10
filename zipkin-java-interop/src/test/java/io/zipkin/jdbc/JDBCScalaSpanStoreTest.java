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
package io.zipkin.jdbc;

import com.twitter.zipkin.storage.SpanStore;
import com.twitter.zipkin.storage.SpanStoreSpec;
import io.zipkin.Annotation;
import io.zipkin.Constants;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import io.zipkin.interop.ScalaSpanStoreAdapter;
import java.sql.SQLException;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class JDBCScalaSpanStoreTest extends SpanStoreSpec {
  private static JDBCSpanStore spanStore;

  @BeforeClass
  public static void setupDB() {
    spanStore = new JDBCTestGraph().spanStore;
  }

  public SpanStore store() {
    return new ScalaSpanStoreAdapter(spanStore);
  }

  public void clear() {
    try {
      spanStore.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }

  long traceId = -692101025335252320L;
  long callQuery = -7842865617155193778L;
  long dbQuery = 8207293009014896295L;
  Endpoint zipkinWeb = Endpoint.create("zipkin-web", 172 << 24 | 17 << 16 | 3, (short) 8080);
  Endpoint zipkinQuery = Endpoint.create("zipkin-query", 172 << 24 | 17 << 16 | 2, (short) 9411);
  Endpoint zipkinJdbc = Endpoint.create("zipkin-jdbc", 172 << 24 | 17 << 16 | 2, 0);

  List<Span> trace = asList(
      new Span.Builder()
          .traceId(traceId)
          .name("GET")
          .id(traceId)
          .addAnnotation(Annotation.create(1444438900939000L, Constants.SERVER_RECV, zipkinWeb))
          .addAnnotation(Annotation.create(1444438901315000L, Constants.SERVER_SEND, zipkinWeb))
          .build(),
      new Span.Builder()
          .traceId(traceId)
          .name("GET")
          .id(callQuery)
          .parentId(traceId)
          .addAnnotation(Annotation.create(1444438900941000L, Constants.CLIENT_SEND, Endpoint.create("zipkin-query", 127 << 24 | 1, 0)))
          .addAnnotation(Annotation.create(1444438900947000L, Constants.SERVER_RECV, zipkinQuery))
          .addAnnotation(Annotation.create(1444438901017000L, Constants.SERVER_SEND, zipkinQuery))
          .addAnnotation(Annotation.create(1444438901018000L, Constants.CLIENT_RECV, Endpoint.create("zipkin-query", 127 << 24 | 1, 0)))
          .build(),
      new Span.Builder()
          .traceId(traceId)
          .name("query")
          .id(dbQuery)
          .parentId(callQuery)
          .addAnnotation(Annotation.create(1444438900948000L, Constants.CLIENT_SEND, zipkinJdbc))
          .addAnnotation(Annotation.create(1444438900979000L, Constants.CLIENT_RECV, zipkinJdbc))
          .build()
  );

  /**
   * Normally, the root-span is where trace id == span id and parent id == null
   */
  @Test
  public void dependencies() {
    spanStore.accept(trace);

    assertThat(spanStore.getDependencies(null, trace.get(0).endTs()).links).containsOnly(
        new DependencyLink.Builder().parent("zipkin-web").child("zipkin-query").callCount(1).build(),
        new DependencyLink.Builder().parent("zipkin-query").child("zipkin-jdbc").callCount(1).build()
    );
  }

  @Test
  public void dependencies_loopback() {
    spanStore.accept(asList(trace.get(0), new Span.Builder()
        .traceId(traceId)
        .name("GET")
        .id(callQuery)
        .parentId(traceId)
        .addAnnotation(Annotation.create(1444438900941000L, Constants.CLIENT_SEND, Endpoint.create("zipkin-web", 127 << 24 | 1, 0)))
        .addAnnotation(Annotation.create(1444438900947000L, Constants.SERVER_RECV, zipkinWeb))
        .addAnnotation(Annotation.create(1444438901017000L, Constants.SERVER_SEND, zipkinWeb))
        .addAnnotation(Annotation.create(1444438901018000L, Constants.CLIENT_RECV, Endpoint.create("zipkin-web", 127 << 24 | 1, 0)))
        .build()));

    assertThat(spanStore.getDependencies(null, trace.get(0).endTs()).links).containsOnly(
        new DependencyLink.Builder().parent("zipkin-web").child("zipkin-web").callCount(1).build()
    );
  }

  /**
   * Some systems log a different trace id than the root span. This seems "headless", as we won't
   * see a span whose id is the same as the trace id.
   */
  @Test
  public void dependencies_headlessTrace() {
    spanStore.accept(asList(trace.get(1), trace.get(2)));

    assertThat(spanStore.getDependencies(null, trace.get(0).endTs()).links).containsOnly(
        new DependencyLink.Builder().parent("zipkin-query").child("zipkin-jdbc").callCount(1).build()
    );
  }
}
