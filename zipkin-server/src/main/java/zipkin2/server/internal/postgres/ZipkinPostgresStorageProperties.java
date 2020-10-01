package zipkin2.server.internal.postgres;

import com.zaxxer.hikari.HikariDataSource;
import java.io.Serializable;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("zipkin.storage.postgres")
public class ZipkinPostgresStorageProperties implements Serializable {
  private static final long serialVersionUID = 0L;

  private String jdbcUrl;
  private String host = "localhost";
  private int port = 5432;
  private String username;
  private String password;
  private String db = "zipkin";
  private String schema = "public";
  private int maxActive = 10;
  private boolean useSsl;

  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public void setJdbcUrl(String jdbcUrl) {
    this.jdbcUrl = jdbcUrl;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = "".equals(username) ? null : username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = "".equals(password) ? null : password;
  }

  public String getDb() {
    return db;
  }

  public void setDb(String db) {
    this.db = db;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public int getMaxActive() {
    return maxActive;
  }

  public void setMaxActive(int maxActive) {
    this.maxActive = maxActive;
  }

  public boolean isUseSsl() {
    return useSsl;
  }

  public void setUseSsl(boolean useSsl) {
    this.useSsl = useSsl;
  }

  public DataSource toDataSource() {
    HikariDataSource result = new HikariDataSource();
    result.setDriverClassName("org.postgresql.Driver");
    result.setJdbcUrl(determineJdbcUrl());
    result.setMaximumPoolSize(getMaxActive());
    result.setUsername(getUsername());
    result.setPassword(getPassword());
    result.setSchema(schema);
    return result;
  }

  private String determineJdbcUrl() {
    if (StringUtils.hasText(getJdbcUrl())) {
      return getJdbcUrl();
    }

    StringBuilder url = new StringBuilder();
    url.append("jdbc:postgresql://");
    url.append(getHost()).append(":").append(getPort());
    url.append("/").append(getDb());
    url.append("?autoReconnect=true");
    url.append("&useSSL=").append(isUseSsl());
    return url.toString();
  }
}
