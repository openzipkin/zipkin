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
package zipkin2.storage.mysql.v1;

import java.util.List;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.DSLContext;
import zipkin2.v1.V1BinaryAnnotation;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

final class SelectAnnotationServiceNames implements Function<DSLContext, List<String>> {
  @Override public List<String> apply(DSLContext context) {
    return context
      .selectDistinct(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
      .from(ZIPKIN_ANNOTATIONS)
      .where(localServiceNameCondition())
      .orderBy(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
      .fetch(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }

  static Condition localServiceNameCondition() {
    return ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.isNotNull()
      .and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.ne("")) // exclude address annotations
      .and(ZIPKIN_ANNOTATIONS.A_TYPE.ne(V1BinaryAnnotation.TYPE_BOOLEAN));
  }

  @Override public String toString() {
    return "SelectAnnotationServiceNames{}";
  }
}
