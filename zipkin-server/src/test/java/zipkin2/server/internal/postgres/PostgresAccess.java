package zipkin2.server.internal.postgres;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** opens package access for testing */
public final class PostgresAccess {

  public static void registerPostgres(AnnotationConfigApplicationContext context) {
    context.register(
      PropertyPlaceholderAutoConfiguration.class, ZipkinPostgresStorageConfiguration.class);
  }
}
