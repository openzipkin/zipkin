/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.postgresql.v1;

import org.jooq.DSLContext;

import java.util.List;
import java.util.function.Function;

import static zipkin2.storage.postgresql.v1.SelectAnnotationServiceNames.localServiceNameCondition;
import static zipkin2.storage.postgresql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.postgresql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class SelectSpanNames implements Function<DSLContext, List<String>> {
  final Schema schema;
  final String serviceName;

  SelectSpanNames(Schema schema, String serviceName) {
    this.schema = schema;
    this.serviceName = serviceName;
  }

  @Override
  public List<String> apply(DSLContext context) {
    return context
      .selectDistinct(ZIPKIN_SPANS.NAME)
      .from(ZIPKIN_SPANS)
      .join(ZIPKIN_ANNOTATIONS)
      .on(schema.joinCondition(ZIPKIN_ANNOTATIONS))
      .where(
        localServiceNameCondition().and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(serviceName)))
      .and(ZIPKIN_SPANS.NAME.notEqual(""))
      .orderBy(ZIPKIN_SPANS.NAME)
      .fetch(ZIPKIN_SPANS.NAME);
  }

  @Override
  public String toString() {
    return "SelectSpanNames{serviceName=" + serviceName + "}";
  }
}
