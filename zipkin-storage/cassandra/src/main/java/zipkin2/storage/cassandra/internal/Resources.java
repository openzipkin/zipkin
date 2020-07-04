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
