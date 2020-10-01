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
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.DSLContext;
import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinAnnotations;
import zipkin2.v1.V1BinaryAnnotation;

final class SelectAnnotationServiceNames implements Function<DSLContext, List<String>> {
  @Override public List<String> apply(DSLContext context) {
    return context
      .selectDistinct(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
      .from(ZipkinAnnotations.ZIPKIN_ANNOTATIONS)
      .where(localServiceNameCondition())
      .orderBy(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
      .fetch(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }

  static Condition localServiceNameCondition() {
    return ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.isNotNull()
      .and(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.ne("")) // exclude address annotations
      .and(ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_TYPE.ne(V1BinaryAnnotation.TYPE_BOOLEAN));
  }

  @Override public String toString() {
    return "SelectAnnotationServiceNames{}";
  }
}
