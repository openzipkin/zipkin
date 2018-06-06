package zipkin2.storage;

import java.util.Iterator;
import java.util.List;
import zipkin2.Call;
import zipkin2.Span;

/**
 * Storage implementation often need to re-check query results when {@link
 * StorageComponent.Builder#strictTraceId(boolean) strict trace ID} is disabled.
 */
public final class StrictTraceId {

  public static Call.Mapper<List<Span>, List<Span>> filterSpans(String traceId) {
    return new FilterSpans(traceId);
  }

  /** Filters the mutable input based on the query */
  public static Call.Mapper<List<List<Span>>, List<List<Span>>> filterTraces(QueryRequest request) {
    return new FilterTraces(request);
  }

  static final class FilterTraces implements Call.Mapper<List<List<Span>>, List<List<Span>>> {

    final QueryRequest request;

    FilterTraces(QueryRequest request) {
      this.request = request;
    }

    @Override
    public List<List<Span>> map(List<List<Span>> input) {
      Iterator<List<Span>> i = input.iterator();
      while (i.hasNext()) { // Not using removeIf as that's java 8+
        List<Span> next = i.next();
        if (next.get(0).traceId().length() > 16 && !request.test(next)) {
          i.remove();
        }
      }
      return input;
    }

    @Override
    public String toString() {
      return "FilterTraces{request=" + request + "}";
    }
  }

  static final class FilterSpans implements Call.Mapper<List<Span>, List<Span>> {

    final String traceId;

    FilterSpans(String traceId) {
      this.traceId = traceId;
    }

    @Override
    public List<Span> map(List<Span> input) {
      Iterator<Span> i = input.iterator();
      while (i.hasNext()) { // Not using removeIf as that's java 8+
        Span next = i.next();
        if (!next.traceId().equals(traceId)) {
          i.remove();
        }
      }
      return input;
    }

    @Override
    public String toString() {
      return "FilterSpans{traceId=" + traceId + "}";
    }
  }
}
