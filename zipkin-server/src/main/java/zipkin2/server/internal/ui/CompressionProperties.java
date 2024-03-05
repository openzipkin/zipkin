/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.ui;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.Compression;

@ConfigurationProperties("server")
class CompressionProperties {
  public Compression getCompression() {
    return compression;
  }

  public void setCompression(Compression compression) {
    this.compression = compression;
  }

  private Compression compression;
}
