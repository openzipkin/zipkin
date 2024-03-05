/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.cassandra3;

import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.storage.cassandra.CassandraStorage;

@ConfigurationProperties("zipkin.storage.cassandra3")
class ZipkinCassandra3StorageProperties implements Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  private String keyspace = "zipkin3";
  private String contactPoints = "localhost";
  private String localDc = "datacenter1";
  private int maxConnections = 8;
  private boolean ensureSchema = true;
  private boolean useSsl = false;
  private boolean sslHostnameValidation = true;
  private String username;
  private String password;
  /** See {@link CassandraStorage.Builder#indexFetchMultiplier(int)} */
  private int indexFetchMultiplier = 3;

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
    this.localDc = "".equals(localDc) ? null : localDc;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public boolean isEnsureSchema() {
    return ensureSchema;
  }

  public void setEnsureSchema(boolean ensureSchema) {
    this.ensureSchema = ensureSchema;
  }

  public boolean isUseSsl() {
    return useSsl;
  }

  public void setUseSsl(boolean useSsl) {
    this.useSsl = useSsl;
  }

  public boolean isSslHostnameValidation() {
    return sslHostnameValidation;
  }

  public void setSslHostnameValidation(boolean sslHostnameValidation) {
    this.sslHostnameValidation = sslHostnameValidation;
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

  public int getIndexFetchMultiplier() {
    return indexFetchMultiplier;
  }

  public void setIndexFetchMultiplier(int indexFetchMultiplier) {
    this.indexFetchMultiplier = indexFetchMultiplier;
  }

  public CassandraStorage.Builder toBuilder() {
    return CassandraStorage.newBuilder()
      .keyspace(keyspace)
      .contactPoints(contactPoints)
      .localDc(localDc)
      .maxConnections(maxConnections)
      .ensureSchema(ensureSchema)
      .useSsl(useSsl)
      .sslHostnameValidation(sslHostnameValidation)
      .username(username)
      .password(password)
      .indexFetchMultiplier(indexFetchMultiplier);
  }
}
