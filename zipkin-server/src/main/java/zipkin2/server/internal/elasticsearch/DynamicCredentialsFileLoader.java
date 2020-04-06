/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.elasticsearch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.PASSWORD_PROP;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.USERNAME_PROP;

/**
 * Loads username/password from credentials file.
 *
 * <p><em>NOTE:</em> This implementation loops instead of using {@link java.nio.file.WatchService}.
 * This means that spans will drop and api failures will occur for any time remaining in the refresh
 * interval. A future version can tighten this by also using poll events.
 *
 * adriancole implementation note: actually if we use a normal single thread scheduled executor and
 * make this type closeable, it should be less error prone on things like this. Spring automatically
 * closes beans on shutdown. Also, this as a bean allows something to synchronously call load auth fail.
 */
class DynamicCredentialsFileLoader {
  static final Logger LOGGER = LoggerFactory.getLogger(DynamicCredentialsFileLoader.class);
  static final String CREDENTIALS_REFRESH_INTERVAL =
    "zipkin.storage.elasticsearch.credentials-refresh-interval";

  private final String credentialsFile;

  private final BasicCredentials basicCredentials;

  public DynamicCredentialsFileLoader(BasicCredentials basicCredentials,
    String credentialsFile) {
    this.basicCredentials = basicCredentials;
    this.credentialsFile = credentialsFile;
  }

  @Scheduled(fixedRateString = "${" + CREDENTIALS_REFRESH_INTERVAL + "}")
  void load() {
    Properties properties = new Properties();
    try {
      File file = Paths.get(credentialsFile).toFile();
      if (!file.exists()) {
        throw new FileNotFoundException("The file does not exist");
      }
      try (FileInputStream is = new FileInputStream(file)) {
        properties.load(is);
        if (!properties.containsKey(USERNAME_PROP) || !properties.containsKey(PASSWORD_PROP)) {
          return;
        }
        basicCredentials.updateCredentials(
          properties.getProperty(USERNAME_PROP),
          properties.getProperty(PASSWORD_PROP)
        );
      }
    } catch (Exception e) {
      LOGGER.error("Load credentials file error", e);
    }
  }
}
