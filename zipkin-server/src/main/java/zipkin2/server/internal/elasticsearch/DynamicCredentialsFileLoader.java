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
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import org.springframework.scheduling.annotation.Scheduled;

import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.PASSWORD_PROP;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.USERNAME_PROP;

/**
 * Load username/password from credentials file.
 */
class DynamicCredentialsFileLoader {
  static final String CREDENTIALS_FILE_REFRESH_INTERVAL_IN_SECOND =
    "zipkin.storage.elasticsearch.credentials-file-refresh-interval-in-second";

  private final String credentialsFilePath;

  private final BasicCredentials basicCredentials;

  public DynamicCredentialsFileLoader(BasicCredentials basicCredentials,
    String credentialsFilePath) {
    this.basicCredentials = basicCredentials;
    this.credentialsFilePath = credentialsFilePath;
  }

  @Scheduled(fixedRateString = "${" + CREDENTIALS_FILE_REFRESH_INTERVAL_IN_SECOND + "}")
  void load() throws IOException {
    Properties properties = new Properties();
    File file = Paths.get(credentialsFilePath).toFile();
    if (!file.getName().endsWith(".properties")) {
      throw new FileNotFoundException("The file does not exist or not end with '.properties'");
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
  }
}
