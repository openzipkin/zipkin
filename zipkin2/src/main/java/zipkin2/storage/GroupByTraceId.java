package zipkin2.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin2.Call;
import zipkin2.Span;

/**
 * A mapper that groups unorganized input spans by trace ID. Useful when preparing a result for
 * {@link SpanStore#getTraces(QueryRequest)}.
 */
public final class GroupByTraceId implements Call.Mapper<List<Span>, List<List<Span>>> {
  public static Call.Mapper<List<Span>, List<List<Span>>> create(boolean strictTraceId) {
    return new GroupByTraceId(strictTraceId);
  }

  final boolean strictTraceId;

  GroupByTraceId(boolean strictTraceId) {
    this.strictTraceId = strictTraceId;
  }

  @Override
  public List<List<Span>> map(List<Span> input) {
    if (input.isEmpty()) return Collections.emptyList();

    Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
    for (Span span : input) {
      String traceId =
          strictTraceId || span.traceId().length() == 16
              ? span.traceId()
              : span.traceId().substring(16);
      if (!groupedByTraceId.containsKey(traceId)) {
        groupedByTraceId.put(traceId, new ArrayList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }
    return new ArrayList<>(groupedByTraceId.values());
  }

  @Override
  public String toString() {
    return "GroupByTraceId{strictTraceId=" + strictTraceId + "}";
  }
}
