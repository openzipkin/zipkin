package zipkin2.storage.clickhouse;

import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

import java.util.List;

public class ClickHouseSpanConsumer implements SpanConsumer {

  @Override
  public Call<Void> accept(List<Span> spans) {
    return null;
  }
}
