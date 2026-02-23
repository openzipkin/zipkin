package zipkin2.storage.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandSettings;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class Schema {

  private final String SCHEMA_PATH = "schema";

  public static void ensure(ClickHouseStorage clickHouseStorage, Client client) {
    if (!clickHouseStorage.isEnsureScheme()) return;

    try (InputStream is = Schema.class.getResourceAsStream("/schema/zipkin-schema-1.sql")) {

      if (is == null) {
        throw new IllegalStateException("clickhouse-schema.sql not found");
      }

      String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

      clickHouseStorage.getClient().execute(sql).get();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize ClickHouse schema", e);
    }
  }
}

