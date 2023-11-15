/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class CassandraConfig extends ModuleConfig {

  private String keyspace = "zipkin3";
  private String contactPoints = "localhost";
  private String localDc = "datacenter1";
  private int maxConnections = 8;
  private boolean useSsl = false;
  private String username;
  private String password;

  private boolean ensureSchema = true;

  /**
   * async batch execute pool size
   */
  protected int asyncBatchPersistentPoolSize  = 4;

  public String getKeyspace() {
    return keyspace;
  }

  public void setKeyspace(String keyspace) {
    this.keyspace = keyspace;
  }

  public String getContactPoints() {
    return contactPoints;
  }

  public void setContactPoints(String contactPoints) {
    this.contactPoints = contactPoints;
  }

  public String getLocalDc() {
    return localDc;
  }

  public void setLocalDc(String localDc) {
    this.localDc = localDc;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public boolean getUseSsl() {
    return useSsl;
  }

  public void setUseSsl(boolean useSsl) {
    this.useSsl = useSsl;
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

  public int getAsyncBatchPersistentPoolSize() {
    return asyncBatchPersistentPoolSize;
  }

  public void setAsyncBatchPersistentPoolSize(int asyncBatchPersistentPoolSize) {
    this.asyncBatchPersistentPoolSize = asyncBatchPersistentPoolSize;
  }

  public boolean getEnsureSchema() {
    return ensureSchema;
  }

  public void setEnsureSchema(boolean ensureSchema) {
    this.ensureSchema = ensureSchema;
  }
}
