package zipkin.filter;

import zipkin.storage.Callback;

import java.util.List;

public interface SpanFilter<S> {
  /**
   * Process filters given a set of spans. The callback should return a FilterActivatedException if filter
   * implementor wants to produce custom return codes back to the user.
   * @param spans
   */
  List<S> process(List<S> spans, Callback<Void> callback);
}
