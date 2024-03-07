/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.SelectTraceIdsFromServiceSpan.Factory.FlatMapServicesToInputs;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;

// TODO: tests use toString because the call composition chain is complex (includes flat mapping)
// This could be made a little less complex if we scrub out map=>map to a list of transformations,
// or possibly special-casing common transformations.
@ExtendWith(MockitoExtension.class)
class CassandraSpanStoreTest {
  @Mock CqlSession session;
  Schema.Metadata metadata = new Schema.Metadata(true, true);
  @Mock KeyspaceMetadata keyspace;
  CassandraSpanStore spanStore;

  @BeforeEach void setup() {
    spanStore = spanStore(CassandraStorage.newBuilder().ensureSchema(false));
  }

  QueryRequest.Builder queryBuilder = QueryRequest.newBuilder().endTs(TODAY).lookback(DAY).limit(5);

  @Test void timestampRange_withIndexTtlProvidedAvoidsOverflow() {
    QueryRequest query = QueryRequest.newBuilder().endTs(TODAY).lookback(TODAY).limit(5).build();
    CassandraSpanStore.TimestampRange timestampRange = spanStore.timestampRange(query, 7890000);

    assertThat(timestampRange.startMillis).isLessThan(timestampRange.endMillis);
  }

  @Test void getTraces_fansOutAgainstServices() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.build());

    assertThat(call.toString()).contains(FlatMapServicesToInputs.class.getSimpleName());
  }

  @Test void getTraces_withSpanNameButNoServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.spanName("get").build());

    assertThat(call.toString())
      .contains(FlatMapServicesToInputs.class.getSimpleName())
      .contains("span=get"); // no need to look at two indexes
  }

  @Test void getTraces_withTagButNoServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.annotationQuery(Map.of("environment", "production")).build());

    assertThat(call.toString())
      .doesNotContain(FlatMapServicesToInputs.class.getSimpleName()) // works against the span table
      .contains("l_service=null, annotation_query=environment=production");
  }

  @Test void getTraces_withDurationButNoServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.minDuration(1000L).build());

    assertThat(call.toString())
      .contains(FlatMapServicesToInputs.class.getSimpleName())
      .contains("start_duration=1,");
  }

  @Test void getTraces_withRemoteServiceNameButNoServiceName() {
    Call<List<List<Span>>> call =
      spanStore.getTraces(queryBuilder.remoteServiceName("backend").build());

    assertThat(call.toString())
      .contains(FlatMapServicesToInputs.class.getSimpleName())
      .contains("remote_service=backend,")
      .doesNotContain("span="); // no need to look at two indexes
  }

  @Test void getTraces() {
    Call<List<List<Span>>> call = spanStore.getTraces(queryBuilder.serviceName("frontend").build());

    assertThat(call.toString()).contains("service=frontend, span=,");
  }

  @Test void getTraces_withSpanName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").spanName("get").build());

    assertThat(call.toString())
      .contains("service=frontend, span=get,");
  }

  @Test void getTraces_withRemoteServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").remoteServiceName("backend").build());

    assertThat(call.toString())
      .contains("service=frontend, remote_service=backend,")
      .doesNotContain("service=frontend, span="); // no need to look at two indexes
  }

  @Test void getTraces_withSpanNameAndRemoteServiceName() {
    Call<List<List<Span>>> call = spanStore.getTraces(
      queryBuilder.serviceName("frontend").remoteServiceName("backend").spanName("get").build());

    assertThat(call.toString()) // needs to look at two indexes
      .contains("service=frontend, remote_service=backend,")
      .contains("service=frontend, span=get,");
  }

  @Test void searchDisabled_doesntMakeRemoteQueryRequests() {
    CassandraSpanStore spanStore = spanStore(CassandraStorage.newBuilder().searchEnabled(false));

    assertThat(spanStore.getTraces(queryBuilder.build())).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getServiceNames()).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getRemoteServiceNames("icecream")).hasToString("ConstantCall{value=[]}");
    assertThat(spanStore.getSpanNames("icecream")).hasToString("ConstantCall{value=[]}");
  }

  CassandraSpanStore spanStore(CassandraStorage.Builder builder) {
    return new CassandraSpanStore(session, metadata, keyspace, builder.maxTraceCols,
      builder.indexFetchMultiplier, builder.strictTraceId, builder.searchEnabled);
  }
}
