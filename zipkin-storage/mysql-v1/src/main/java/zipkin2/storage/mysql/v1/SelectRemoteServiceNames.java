/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.List;
import java.util.function.Function;
import org.jooq.DSLContext;

import static zipkin2.storage.mysql.v1.SelectAnnotationServiceNames.localServiceNameCondition;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class SelectRemoteServiceNames implements Function<DSLContext, List<String>> {
  final Schema schema;
  final String serviceName;

  SelectRemoteServiceNames(Schema schema, String serviceName) {
    this.schema = schema;
    this.serviceName = serviceName;
  }

  @Override
  public List<String> apply(DSLContext context) {
    return context
      .selectDistinct(ZIPKIN_SPANS.REMOTE_SERVICE_NAME)
      .from(ZIPKIN_SPANS)
      .join(ZIPKIN_ANNOTATIONS)
      .on(schema.joinCondition(ZIPKIN_ANNOTATIONS))
      .where(
        localServiceNameCondition().and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.eq(serviceName)))
      .and(ZIPKIN_SPANS.REMOTE_SERVICE_NAME.notEqual(""))
      .orderBy(ZIPKIN_SPANS.REMOTE_SERVICE_NAME)
      .fetch(ZIPKIN_SPANS.REMOTE_SERVICE_NAME);
  }

  @Override
  public String toString() {
    return "SelectRemoteServiceNames{serviceName=" + serviceName + "}";
  }
}
