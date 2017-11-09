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
package zipkin.autoconfigure.storage.influxdb;

import zipkin2.storage.influxdb.InfluxDBStorage;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("zipkin.storage.influxdb")
public class ZipkinInfluxDBStorageProperties {
  private String url = "http://localhost:8086";
  private String password = "";
  private String username = "root";
  private String retentionPolicy = "default";
  private String database = "zipkin";
  private String measurement = "zipkin";

  public String getMeasurement() {
    return measurement;
  }

  public void setMeasurement(String measurement) {
    this.measurement = measurement;
  }

  public String getRetentionPolicy() {
    return retentionPolicy;
  }

  public void setRetentionPolicy(String retention_policy) {
    this.retentionPolicy = retention_policy;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getAddress() {
    return url;
  }

  public void setAddress(String url) {
    this.url = url;
  }

  public InfluxDBStorage.Builder toBuilder() {
    return InfluxDBStorage.newBuilder()
      .url(url)
      .database(database)
      .username(username)
      .password(password)
      .retentionPolicy(retentionPolicy)
      .measurement(measurement);
  }
}
