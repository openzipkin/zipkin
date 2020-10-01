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
package zipkin2.storage.postgres.v1;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jooq.DSLContext;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinAnnotations;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinSpans;

final class SelectSpanNames implements Function<DSLContext, List<String>> {
  final Schema schema;
  final String serviceName;

  SelectSpanNames(Schema schema, String serviceName) {
    this.schema = schema;
    this.serviceName = serviceName.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public List<String> apply(DSLContext context) {
    return context
      .selectDistinct(ZipkinSpans.ZIPKIN_SPANS.NAME)
      .from(ZipkinSpans.ZIPKIN_SPANS)
      .join(ZipkinAnnotations.ZIPKIN_ANNOTATIONS)
      .on(schema.joinCondition(ZipkinAnnotations.ZIPKIN_ANNOTATIONS))
      .where(
        SelectAnnotationServiceNames
            .localServiceNameCondition().and(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(serviceName)))
      .and(ZipkinSpans.ZIPKIN_SPANS.NAME.notEqual(""))
      .orderBy(ZipkinSpans.ZIPKIN_SPANS.NAME)
      .fetch(ZipkinSpans.ZIPKIN_SPANS.NAME);
  }

  @Override
  public String toString() {
    return "SelectSpanNames{serviceName=" + serviceName + "}";
  }
}
