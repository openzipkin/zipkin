/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import zipkin.DependencyLink;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static zipkin.internal.V2SpanConverter.fromLinks;
import static zipkin.internal.V2SpanConverter.toLinks;

/**
 * This parses a span tree into dependency links used by Web UI. Ex. http://zipkin/dependency
 *
 * <p>This implementation traverses the tree, and only creates links between {@link Kind#SERVER
 * server} spans. One exception is at the bottom of the trace tree. {@link Kind#CLIENT client} spans
 * that record their {@link Span#remoteEndpoint()} are included, as this accounts
 * for uninstrumented services. Spans with {@link Span#kind()} unset, but {@link
 * Span#remoteEndpoint()} set are treated the same as client spans.
 */
public final class DependencyLinker {
  private final zipkin2.internal.DependencyLinker delegate;

  public DependencyLinker() {
    this.delegate = new zipkin2.internal.DependencyLinker();
  }

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(Collection<zipkin.Span> spans) {
    if (spans.isEmpty()) return this;

    List<Span> linkSpans = new ArrayList<>();
    for (zipkin.Span s : MergeById.apply(spans)) {
      linkSpans.addAll(V2SpanConverter.fromSpan(s));
    }
    delegate.putTrace(linkSpans.iterator());
    return this;
  }

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(Iterator<Span> spans) {
    delegate.putTrace(spans);
    return this;
  }

  public List<DependencyLink> link() {
    return toLinks(delegate.link());
  }

  /** links are merged by mapping to parent/child and summing corresponding links */
  public static List<DependencyLink> merge(Iterable<DependencyLink> in) {
    return toLinks(zipkin2.internal.DependencyLinker.merge(fromLinks(in)));
  }
}
