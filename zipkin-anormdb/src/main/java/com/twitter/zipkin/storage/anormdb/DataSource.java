package com.twitter.zipkin.storage.anormdb;

import java.sql.Connection;
import java.sql.SQLException;

final class DataSource {
  private final com.zaxxer.hikari.HikariDataSource delegate;

  DataSource(String driver, String location, boolean jdbc3) {
    delegate = new com.zaxxer.hikari.HikariDataSource();
    delegate.setDriverClassName(driver);
    delegate.setJdbcUrl(location);
    delegate.setConnectionTestQuery(jdbc3 ? "SELECT 1" : null);
    delegate.setMaximumPoolSize(32);
  }

  void close() throws SQLException {
    delegate.close();
  }

  Connection getConnection() throws SQLException {
    return delegate.getConnection();
  }
}
