/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.internal;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class Util {
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  public static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  public static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }

  public static boolean equal(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkArgument}.
   */
  public static void checkArgument(boolean expression,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
   */
  public static <T> T checkNotNull(T reference, String errorMessage) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(errorMessage);
    }
    return reference;
  }

  public static <T extends Comparable<? super T>> List<T> sortedList(@Nullable Collection<T> input) {
    if (input == null || input.isEmpty()) return Collections.emptyList();
    if (input.size() == 1) return Collections.singletonList(input.iterator().next());
    List<T> result = new ArrayList<>(input);
    Collections.sort(result);
    return Collections.unmodifiableList(result);
  }

  private Util() {
  }
}
