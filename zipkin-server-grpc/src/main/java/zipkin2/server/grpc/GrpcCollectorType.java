package zipkin2.server.grpc;

import io.grpc.BindableService;

public enum GrpcCollectorType {
  GRPC_JAVA,
  ARMERIA;

  public GrpcCollector newCollector(GrpcCollector.Builder builder, BindableService spanService) {
    if (this == GrpcCollectorType.ARMERIA) {
      return new ArmeriaGrpcCollector(spanService, builder.port);
    } else {
      return new GrpcJavaCollector(spanService, builder.port);
    }
  }
}
