/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.interop;

import com.twitter.util.Future;
import com.twitter.zipkin.common.Dependencies;
import com.twitter.zipkin.common.DependencyLink;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import java.util.ArrayList;
import java.util.List;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;

/**
 * Adapts {@link SpanStore} to a scala {@link com.twitter.zipkin.storage.DependencyStore} in order
 * to test against its {@link com.twitter.zipkin.storage.DependencyStoreSpec} for interoperability
 * reasons.
 *
 * <p/> This implementation uses json to ensure structures are compatible.
 */
public final class ScalaDependencyStoreAdapter extends com.twitter.zipkin.storage.DependencyStore {
  private final SpanStore spanStore;

  public ScalaDependencyStoreAdapter(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public Option<Object> getDependencies$default$2() {
    return Option.empty();
  }

  @Override
  public Future<Seq<DependencyLink>> getDependencies(long endTs, Option<Object> lookback) {
    List<io.zipkin.DependencyLink> input = spanStore.getDependencies(endTs,
        lookback.isDefined() ? (Long) lookback.get() : null
    );
    List<DependencyLink> links = new ArrayList<>(input.size());
    for (io.zipkin.DependencyLink link : input) {
      DependencyLink converted = convert(link);
      if (converted != null) {
        links.add(converted);
      }
    }
    return Future.value(JavaConversions.asScalaBuffer(links).seq());
  }

  @Override
  public Future<BoxedUnit> storeDependencies(Dependencies dependencies) {
    return Future.Unit();
  }

  @Override
  public void close() {
    this.spanStore.close();
  }

  @Nullable
  static DependencyLink convert(io.zipkin.DependencyLink input) {
    return new DependencyLink(input.parent, input.child, input.callCount);
  }
}
