/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.mysql.v1;

import java.util.List;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.Record;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;

import static zipkin2.storage.mysql.v1.Schema.maybeGet;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

final class SelectDependencies implements Function<DSLContext, List<DependencyLink>> {
  final Schema schema;
  final List<Long> epochDays;

  SelectDependencies(Schema schema, List<Long> epochDays) {
    this.schema = schema;
    this.epochDays = epochDays;
  }

  @Override
  public List<DependencyLink> apply(DSLContext context) {
    List<DependencyLink> unmerged =
        context
            .select(schema.dependencyLinkFields)
            .from(ZIPKIN_DEPENDENCIES)
            .where(ZIPKIN_DEPENDENCIES.DAY.in(epochDays))
            .fetch(
                (Record l) ->
                    DependencyLink.newBuilder()
                        .parent(l.get(ZIPKIN_DEPENDENCIES.PARENT))
                        .child(l.get(ZIPKIN_DEPENDENCIES.CHILD))
                        .callCount(l.get(ZIPKIN_DEPENDENCIES.CALL_COUNT))
                        .errorCount(maybeGet(l, ZIPKIN_DEPENDENCIES.ERROR_COUNT, 0L))
                        .build());
    return DependencyLinker.merge(unmerged);
  }

  @Override
  public String toString() {
    return "SelectDependencies{epochDays=" + epochDays + "}";
  }
}
