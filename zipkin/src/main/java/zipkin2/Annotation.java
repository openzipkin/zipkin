/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 * Associates an event that explains latency with a timestamp.
 *
 * <p>Unlike log statements, annotations are often codes: Ex. {@code cache.miss}.
 */
//@Immutable
public final class Annotation implements Comparable<Annotation>, Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  public static Annotation create(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    return new Annotation(timestamp, value);
  }

  /**
   * Microseconds from epoch.
   *
   * <p>This value should be set directly by instrumentation, using the most precise value possible.
   * For example, {@code gettimeofday} or multiplying {@link System#currentTimeMillis} by 1000.
   */
  public long timestamp() {
    return timestamp;
  }

  /**
   * Usually a short tag indicating an event, like {@code cache.miss} or {@code error}
   */
  public String value() {
    return value;
  }


  /** Compares by {@link #timestamp}, then {@link #value}. */
  @Override public int compareTo(Annotation that) {
    if (this == that) return 0;
    int byTimestamp = timestamp() < that.timestamp() ? -1 : timestamp() == that.timestamp() ? 0 : 1;
    if (byTimestamp != 0) return byTimestamp;
    return value().compareTo(that.value());
  }

  // clutter below mainly due to difficulty working with Kryo which cannot handle AutoValue subclass
  // See https://github.com/openzipkin/zipkin/issues/1879
  final long timestamp;
  final String value;

  Annotation(long timestamp, String value) {
    this.timestamp = timestamp;
    this.value = value;
  }

  @Override public String toString() {
    return "Annotation{"
      + "timestamp=" + timestamp + ", "
      + "value=" + value
      + "}";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Annotation)) return false;
    Annotation that = (Annotation) o;
    return timestamp == that.timestamp() && value.equals(that.value());
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((timestamp >>> 32) ^ timestamp);
    h *= 1000003;
    h ^= value.hashCode();
    return h;
  }

  // As this is an immutable object (no default constructor), defer to a serialization proxy.
  Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(this);
  }

  private static final class SerializedForm implements Serializable {
    static final long serialVersionUID = 0L;

    final long timestamp;
    final String value;

    SerializedForm(Annotation annotation) {
      timestamp = annotation.timestamp;
      value = annotation.value;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return Annotation.create(timestamp, value);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
