/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Resources {
  public static String resourceToString(String resource) {
    try (
      Reader reader = new InputStreamReader(Resources.class.getResourceAsStream(resource), UTF_8)) {
      char[] buf = new char[2048];
      StringBuilder builder = new StringBuilder();
      int read;
      while ((read = reader.read(buf)) != -1) {
        builder.append(buf, 0, read);
      }
      return builder.toString();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  Resources() {
  }
}
