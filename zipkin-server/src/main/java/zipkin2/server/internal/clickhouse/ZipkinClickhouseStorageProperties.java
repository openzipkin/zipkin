package zipkin2.server.internal.clickhouse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.storage.clickhouse.ClickHouseStorage;

@ConfigurationProperties("zipkin.storage.clickhouse")
class ZipkinClickhouseStorageProperties {

  private String database = "zipkin";
  private String host = "localhost";
  private int port = 8123;
  private String username = "zipkin";
  private String password = "zipkin";

  public ClickHouseStorage.Builder toStorageBuilder() {
    return new ClickHouseStorage.Builder()
      .setHost(host)
      .setPort(port)
      .setUsername(username)
      .setPassword(password)
      .setDatabase(database);
  }
}
