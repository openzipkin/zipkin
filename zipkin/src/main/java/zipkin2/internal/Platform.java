/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.internal;

import java.io.IOException;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

/**
 * Provides access to platform-specific features.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class Platform {
  private static final Platform PLATFORM = findPlatform();

  Platform() {
  }

  static final ThreadLocal<char[]> SHORT_STRING_BUFFER = new ThreadLocal<>();
  /** Maximum character length constraint of most names, IP literals and IDs. */
  public static final int SHORT_STRING_LENGTH = 256;

  /**
   * Returns a {@link ThreadLocal} reused {@code char[]} for use when decoding bytes into hex, IP
   * literals, or {@link #SHORT_STRING_LENGTH short strings}. The buffer must never be leaked
   * outside the method. Most will {@link String#String(char[], int, int) copy it into a string}.
   */
  public static char[] shortStringBuffer() {
    char[] shortStringBuffer = SHORT_STRING_BUFFER.get();
    if (shortStringBuffer == null) {
      shortStringBuffer = new char[SHORT_STRING_LENGTH];
      SHORT_STRING_BUFFER.set(shortStringBuffer);
    }
    return shortStringBuffer;
  }

  public RuntimeException uncheckedIOException(IOException e) {
    return new RuntimeException(e);
  }

  public AssertionError assertionError(String message, Throwable cause) {
    AssertionError error = new AssertionError(message);
    error.initCause(cause);
    throw error;
  }

  public static Platform get() {
    return PLATFORM;
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  static Platform findPlatform() {
    // Find JRE 8 new types
    try {
      Class.forName("java.io.UncheckedIOException");
      return new Jre8(); // intentionally doesn't not access the type prior to the above guard
    } catch (ClassNotFoundException e) {
      // pre JRE 8
    }

    // Find JRE 7 new types
    try {
      Class.forName("java.util.concurrent.ThreadLocalRandom");
      return new Jre7(); // intentionally doesn't not access the type prior to the above guard
    } catch (ClassNotFoundException e) {
      // pre JRE 7
    }

    // compatible with JRE 6
    return Jre6.build();
  }

  static final class Jre8 extends Jre7 {
    @IgnoreJRERequirement @Override public RuntimeException uncheckedIOException(IOException e) {
      return new java.io.UncheckedIOException(e);
    }
  }

  static class Jre7 extends Platform {
    @IgnoreJRERequirement @Override
    public AssertionError assertionError(String message, Throwable cause) {
      return new AssertionError(message, cause);
    }
  }

  static final class Jre6 extends Platform {
    static Jre6 build() {
      return new Jre6();
    }
  }
}
