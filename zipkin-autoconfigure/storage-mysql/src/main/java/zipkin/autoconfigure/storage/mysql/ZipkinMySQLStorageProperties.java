/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.mysql;

import com.zaxxer.hikari.HikariDataSource;
import java.io.Serializable;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zipkin.storage.mysql")
public class ZipkinMySQLStorageProperties implements Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  private String host = "localhost";
  private int port = 3306;
  private String username;
  private String password;
  private String db = "zipkin";
  private int maxActive = 10;
  private boolean useSsl;

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
    StringBuilder url = new StringBuilder("jdbc:mysql://");
    url.append(getHost()).append(":").append(getPort());
    url.append("/").append(getDb());
    url.append("?autoReconnect=true");
    url.append("&useSSL=").append(isUseSsl());
    url.append("&useUnicode=yes&characterEncoding=UTF-8");
    HikariDataSource result = new HikariDataSource();
    result.setDriverClassName("org.mariadb.jdbc.Driver");
    result.setJdbcUrl(url.toString());
    result.setMaximumPoolSize(getMaxActive());
    result.setUsername(getUsername());
    result.setPassword(getPassword());
    return result;
  }
}
