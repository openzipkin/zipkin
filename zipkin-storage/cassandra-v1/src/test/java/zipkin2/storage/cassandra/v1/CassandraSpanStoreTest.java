/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.ProtocolVersion;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.v1.SelectTraceIdTimestampFromServiceNames.Factory.FlatMapServiceNamesToInput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;

// TODO: tests use toString because the call composition chain is complex (includes flat mapping)
// This could be made a little less complex if we scrub out map=>map to a list of transformations,
// or possibly special-casing common transformations.
public class CassandraSpanStoreTest {
  CassandraSpanStore spanStore = spanStore(CassandraStorage.newBuilder());
  QueryRequest.Builder queryBuilder = QueryRequest.newBuilder().endTs(TODAY).lookback(DAY).limit(5);

  @Test public void getTraces_fansOutAgainstServices() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.build());

    assertThat(call.toString()).contains(FlatMapServiceNamesToInput.class.getSimpleName());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTraces_withSpanNameButNoServiceName() {
    spanStore.getTraces(queryBuilder.spanName("get").build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTraces_withTagButNoServiceName() {
    spanStore.getTraces(
      queryBuilder.annotationQuery(Collections.singletonMap("environment", "production")).build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTraces_withDurationButNoServiceName() {
    spanStore.getTraces(queryBuilder.minDuration(100L).build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTraces_withRemoteServiceNameButNoServiceName() {
    spanStore.getTraces(queryBuilder.remoteServiceName("backend").build());
  }

  @Test public void getTraces() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.serviceName("frontend").build());

    assertThat(call.toString()).contains("service_name=frontend,");
  }

  @Test public void getTraces_withSpanName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").spanName("get").build());

    assertThat(call.toString()).contains("service_span_name=frontend.get,");
  }

  @Test public void getTraces_withRemoteServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").remoteServiceName("backend").build());

    assertThat(call.toString())
      .contains("service_remote_service_name=frontend.backend,")
      .doesNotContain("service=frontend, span="); // no need to look at two indexes
  }

  @Test public void getTraces_withSpanNameAndRemoteServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").remoteServiceName("backend").spanName("get").build());

    assertThat(call.toString()) // needs to look at two indexes
      .contains("service_remote_service_name=frontend.backend,")
      .contains("service_span_name=frontend.get,");
  }

  @Test public void searchDisabled_doesntMakeRemoteQueryRequests() {
    CassandraSpanStore spanStore = spanStore(CassandraStorage.newBuilder().searchEnabled(false));

    assertThat(spanStore.getTraces(queryBuilder.build())).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getServiceNames()).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getRemoteServiceNames("icecream")).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getSpanNames("icecream")).hasToString("ConstantCall{value=[]}");
  }

  static CassandraSpanStore spanStore(CassandraStorage.Builder builder) {
    CassandraStorage storage =
      spy(builder.sessionFactory(mock(SessionFactory.class, Mockito.RETURNS_MOCKS)).build());
    doReturn(new Schema.Metadata(ProtocolVersion.V4, "", true, true, true))
      .when(storage).metadata();
    return new CassandraSpanStore(storage);
  }
}
