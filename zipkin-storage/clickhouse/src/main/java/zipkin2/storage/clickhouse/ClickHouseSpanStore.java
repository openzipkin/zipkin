package zipkin2.storage.clickhouse;

import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

import java.util.List;

public class ClickHouseSpanStore implements SpanStore {

  @Override
  public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    return null;
  }

  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    return null;
  }

  @Override
  public Call<List<Span>> getTrace(String traceId) {
    return null;
  }

  @Override
  public Call<List<String>> getServiceNames() {
    return null;
  }

  @Override
  public Call<List<String>> getSpanNames(String serviceName) {
    return null;
  }
}
