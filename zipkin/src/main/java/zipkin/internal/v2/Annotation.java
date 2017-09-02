/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal.v2;

import com.google.auto.value.AutoValue;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/**
 * Associates an event that explains latency with a timestamp.
 *
 * <p>Unlike log statements, annotations are often codes: Ex. {@link "cache.miss"}.
 */
@AutoValue
@Immutable
public abstract class Annotation implements Comparable<Annotation>, Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  public static Annotation create(long timestamp, String value) {
    return new AutoValue_Annotation(timestamp, value);
  }

  /**
   * Microseconds from epoch.
   *
   * <p>This value should be set directly by instrumentation, using the most precise value possible.
   * For example, {@code gettimeofday} or multiplying {@link System#currentTimeMillis} by 1000.
   */
  public abstract long timestamp();

  /**
   * Usually a short tag indicating an event, like {@code cache.miss} or {@code error}
   */
  public abstract String value();

  /** Compares by {@link #timestamp}, then {@link #value}. */
  @Override public int compareTo(Annotation that) {
    if (this == that) return 0;
    int byTimestamp = timestamp() < that.timestamp() ? -1 : timestamp() == that.timestamp() ? 0 : 1;
    if (byTimestamp != 0) return byTimestamp;
    return value().compareTo(that.value());
  }

  Annotation() {
  }
}
