/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage.cassandra;

import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.SelectTraceIdsFromServiceSpan.Factory.FlatMapServicesToInputs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;

public class CassandraSpanStoreTest {
  CassandraSpanStore spanStore = spanStore(CassandraStorage.newBuilder());

  @Test public void getTraces_fansOutAgainstServices() {
    Call<List<List<Span>>> call = spanStore.getTraces(QueryRequest.newBuilder()
      .endTs(TODAY).lookback(DAY).limit(10).build());

    // TODO: the composition chain could be made a little complex if we scrub out map,map to
    // a list of transformations, or possibly just one special-cased one
    assertThat(call.toString())
      .contains(FlatMapServicesToInputs.class.getSimpleName());
  }

  @Test public void searchDisabled_doesntMakeRemoteQueryRequests() {
    CassandraSpanStore spanStore = spanStore(CassandraStorage.newBuilder().searchEnabled(false));

    assertThat(spanStore.getTraces(
      QueryRequest.newBuilder().endTs(TODAY).lookback(10000L).limit(10).build()
    )).hasToString("ConstantCall{value=[]}");

    assertThat(spanStore.getServiceNames()).hasToString("ConstantCall{value=[]}");

    assertThat(spanStore.getSpanNames("icecream")).hasToString("ConstantCall{value=[]}");
  }

  static CassandraSpanStore spanStore(CassandraStorage.Builder builder) {
    return new CassandraSpanStore(
      builder.sessionFactory(mock(CassandraStorage.SessionFactory.class, Mockito.RETURNS_MOCKS))
        .build()) {
    };
  }
}
