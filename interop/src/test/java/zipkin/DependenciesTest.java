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
package zipkin;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test for {@link SpanStore} implementations that support dependency aggregation. Subtypes
 * should create a connection to a real backend, even if that backend is in-process.
 *
 * <p/>This is a replacement for {@code com.twitter.zipkin.storage.DependencyStoreSpec}.
 */
public abstract class DependenciesTest<T extends SpanStore> {

  /** Should maintain state between multiple calls within a test. */
  protected final T store;

  protected DependenciesTest(T store) {
    this.store = store;
  }

  /** Clears the span store between tests. */
  @Before
  public abstract void clear();

  /**
   * Implementations should at least {@link SpanStore#accept(Iterator) store} the input. If
   * dependency processing is a separate job, it should complete before returning from this method.
   */
  protected abstract void processDependencies(List<Span> spans);


  /** Notably, the cassandra implementation has day granularity */
  private static long midnight(){
    Calendar date = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
    // reset hour, minutes, seconds and millis
    date.set(Calendar.HOUR_OF_DAY, 0);
    date.set(Calendar.MINUTE, 0);
    date.set(Calendar.SECOND, 0);
    date.set(Calendar.MILLISECOND, 0);
    return date.getTimeInMillis();
  }

  // Use real time, as most span-stores have TTL logic which looks back several days.
  long today = midnight();

  Endpoint zipkinWeb = Endpoint.create("zipkin-web", 172 << 24 | 17 << 16 | 3, 8080);
  Endpoint zipkinQuery = Endpoint.create("zipkin-query", 172 << 24 | 17 << 16 | 2, 9411);
  Endpoint zipkinJdbc = Endpoint.create("zipkin-jdbc", 172 << 24 | 17 << 16 | 2, 0);

  /** This test confirms that core ("sr", "cs", "cr", "ss") annotations are not required. */
  @Test
  public void getDependencies_noCoreAnnotations() {
    Endpoint someClient = Endpoint.create("some-client", 172 << 24 | 17 << 16 | 4, 80);
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, someClient))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 150) * 1000).duration(50L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinJdbc)).build()
    );

    processDependencies(trace);

    assertThat(store.getDependencies(today * 1000, null)).containsOnly(
        new DependencyLink("some-client", "zipkin-web", 1),
        new DependencyLink("zipkin-web", "zipkin-query", 1),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate
   * spans like the below properly.
   *
   *   span1: SR SS
   *     span2: intermediate call
   *       span3: CS SR SS CR: Dependency 1
   */
  @Test
  public void getDependencies_intermediateSpans() {
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, Constants.SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, Constants.SERVER_SEND, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(20L).id(21L).name("call")
            .timestamp((today + 25) * 1000).duration(325L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth2", zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, Constants.CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 100) * 1000, Constants.SERVER_RECV, zipkinQuery))
            .addAnnotation(Annotation.create((today + 250) * 1000, Constants.SERVER_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 300) * 1000, Constants.CLIENT_RECV, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(22L).id(23L).name("call")
            .timestamp((today + 110) * 1000).duration(130L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth4", zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(23L).id(24L).name("call")
            .timestamp((today + 125) * 1000).duration(105L * 1000)
            .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "depth5", zipkinQuery)).build(),
        new Span.Builder().traceId(20L).parentId(24L).id(25L).name("get")
            .timestamp((today + 150) * 1000).duration(50L * 1000)
            .addAnnotation(Annotation.create((today + 150) * 1000, Constants.CLIENT_SEND, zipkinQuery))
            .addAnnotation(Annotation.create((today + 200) * 1000, Constants.CLIENT_RECV, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinJdbc)).build()
    );

    processDependencies(trace);

    assertThat(store.getDependencies(today * 1000, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 1),
        new DependencyLink("zipkin-query", "zipkin-jdbc", 1)
    );
  }

  /**
   * This test confirms that the span store can process trace with intermediate
   * spans like the below properly.
   *
   *   span1: SR SS
   *     span2: intermediate call
   *       span3: CS SR SS CR: Dependency 1
   */
  @Test
  public void getDependencies_duplicateAddress() {
    List<Span> trace = asList(
        new Span.Builder().traceId(20L).id(20L).name("get")
            .timestamp(today * 1000).duration(350L * 1000)
            .addAnnotation(Annotation.create(today * 1000, Constants.SERVER_RECV, zipkinWeb))
            .addAnnotation(Annotation.create((today + 350) * 1000, Constants.SERVER_SEND, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinWeb)).build(),
        new Span.Builder().traceId(20L).parentId(21L).id(22L).name("get")
            .timestamp((today + 50) * 1000).duration(250L * 1000)
            .addAnnotation(Annotation.create((today + 50) * 1000, Constants.CLIENT_SEND, zipkinWeb))
            .addAnnotation(Annotation.create((today + 300) * 1000, Constants.CLIENT_RECV, zipkinWeb))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinQuery)).build()
    );

    processDependencies(trace);

    assertThat(store.getDependencies(today * 1000, null)).containsOnly(
        new DependencyLink("zipkin-web", "zipkin-query", 1)
    );
  }
}
