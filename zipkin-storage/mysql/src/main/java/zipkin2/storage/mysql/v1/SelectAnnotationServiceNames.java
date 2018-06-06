package zipkin2.storage.mysql.v1;

import java.util.List;
import java.util.function.Function;
import org.jooq.DSLContext;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

final class SelectAnnotationServiceNames implements Function<DSLContext, List<String>> {
  @Override
  public List<String> apply(DSLContext context) {
    return context
        .selectDistinct(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME)
        .from(ZIPKIN_ANNOTATIONS)
        .where(
            ZIPKIN_ANNOTATIONS
                .ENDPOINT_SERVICE_NAME
                .isNotNull()
                .and(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME.ne("")))
        .fetch(ZIPKIN_ANNOTATIONS.ENDPOINT_SERVICE_NAME);
  }

  @Override
  public String toString() {
    return "SelectAnnotationServiceNames{}";
  }
}
