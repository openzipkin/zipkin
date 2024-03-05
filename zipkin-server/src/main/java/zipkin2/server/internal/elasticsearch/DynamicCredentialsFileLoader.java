/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.internal.Nullable;

import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.PASSWORD;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.USERNAME;

/**
 * Loads username/password from credentials file.
 *
 * <p><em>NOTE:</em> This implementation loops instead of using {@link java.nio.file.WatchService}.
 * This means that spans will drop and api failures will occur for any time remaining in the refresh
 * interval. A future version can tighten this by also using poll events.
 */
class DynamicCredentialsFileLoader implements Runnable {
  static final Logger LOGGER = LoggerFactory.getLogger(DynamicCredentialsFileLoader.class);

  private final String credentialsFile;

  private final BasicCredentials basicCredentials;

  public DynamicCredentialsFileLoader(BasicCredentials basicCredentials,
    String credentialsFile) {
    this.basicCredentials = basicCredentials;
    this.credentialsFile = credentialsFile;
  }

  @Override public void run() {
    try {
      updateCredentialsFromProperties();
    } catch (Exception e) {
      LOGGER.error("Error loading elasticsearch credentials", e);
    }
  }

  void updateCredentialsFromProperties() throws IOException {
    Properties properties = new Properties();
    try (FileInputStream is = new FileInputStream(credentialsFile)) {
      properties.load(is);
    }
    String username = ensureNotEmptyOrNull(properties, credentialsFile, USERNAME);
    String password = ensureNotEmptyOrNull(properties, credentialsFile, PASSWORD);
    basicCredentials.updateCredentials(username, password);
  }

  @Nullable static String ensureNotEmptyOrNull(Properties properties, String fileName, String name) {
    String value = properties.getProperty(name);
    if (value == null) {
      throw new IllegalStateException("no " + name + " property in " + fileName);
    }
    value = value.trim();
    if (value.isEmpty()) {
      throw new IllegalStateException("empty " + name + " property in " + fileName);
    }
    return value;
  }
}
