/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.interop;

import com.twitter.util.Future;
import com.twitter.zipkin.common.Dependencies;
import com.twitter.zipkin.common.DependencyLink;
import java.util.ArrayList;
import java.util.List;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;
import zipkin.AsyncSpanStore;

import static zipkin.interop.CloseAdapter.closeQuietly;

/**
 * Adapts {@link AsyncSpanStore} to a scala {@link com.twitter.zipkin.storage.DependencyStore} in
 * order to test against its {@link com.twitter.zipkin.storage.DependencyStoreSpec} for
 * interoperability reasons.
 *
 * <p> This implementation uses json to ensure structures are compatible.
 */
public final class ScalaDependencyStoreAdapter extends com.twitter.zipkin.storage.DependencyStore {
  private final AsyncSpanStore spanStore;

  public ScalaDependencyStoreAdapter(AsyncSpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public Option<Object> getDependencies$default$2() {
    return Option.empty();
  }

  @Override
  public Future<Seq<DependencyLink>> getDependencies(long endTs, Option<Object> lookback) {
    GetDependenciesCallback callback = new GetDependenciesCallback();
    spanStore.getDependencies(endTs, lookback.isDefined() ? (Long) lookback.get() : null, callback);
    return callback.promise;
  }

  static final class GetDependenciesCallback
      extends CallbackWithPromise<List<zipkin.DependencyLink>, Seq<DependencyLink>> {

    @Override protected Seq<DependencyLink> convertToScala(List<zipkin.DependencyLink> input) {
      List<DependencyLink> links = new ArrayList<>(input.size());
      for (zipkin.DependencyLink link : input) {
        links.add(new DependencyLink(link.parent, link.child, link.callCount));
      }
      return JavaConversions.asScalaBuffer(links).seq();
    }
  }

  @Override
  public Future<BoxedUnit> storeDependencies(Dependencies dependencies) {
    return Future.Unit();
  }

  @Override
  public void close() {
    closeQuietly(spanStore);
  }
}
