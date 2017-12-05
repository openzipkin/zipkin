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
package zipkin;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import zipkin.internal.Nullable;
import zipkin.storage.StorageComponent;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.equal;
import static zipkin.internal.Util.sortedList;
import static zipkin.internal.Util.writeHexLong;

/**
 * A trace is a series of spans (often RPC calls) which form a latency tree.
 *
 * <p>Spans are usually created by instrumentation in RPC clients or servers, but can also
 * represent in-process activity. Annotations in spans are similar to log statements, and are
 * sometimes created directly by application developers to indicate events of interest, such as a
 * cache miss.
 *
 * <p>The root span is where {@link #parentId} is null; it usually has the longest {@link #duration} in the
 * trace.
 *
 * <p>Span identifiers are packed into longs, but should be treated opaquely. ID encoding is
 * 16 or 32 character lower-hex, to avoid signed interpretation.
 */
public final class Span implements Comparable<Span>, Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  /**
   * When non-zero, the trace containing this span uses 128-bit trace identifiers.
   *
   * <p>{@code traceIdHigh} corresponds to the high bits in big-endian format and {@link #traceId}
   * corresponds to the low bits.
   *
   * <p>Ex. to convert the two fields to a 128bit opaque id array, you'd use code like below.
   * <pre>{@code
   * ByteBuffer traceId128 = ByteBuffer.allocate(16);
   * traceId128.putLong(span.traceIdHigh);
   * traceId128.putLong(span.traceId);
   * traceBytes = traceId128.array();
   * }</pre>
   *
   * @see StorageComponent.Builder#strictTraceId(boolean)
   */
  public final long traceIdHigh;

  /**
   * Unique 8-byte identifier for a trace, set on all spans within it.
   *
   * @see #traceIdHigh for notes about 128-bit trace identifiers
   */
  public final long traceId;

  /**
   * Span name in lowercase, rpc method for example.
   *
   * <p>Conventionally, when the span name isn't known, name = "unknown".
   */
  public final String name;

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@code #id}).
   */
  public final long id;

  /**
   * The parent's {@link #id} or null if this the root span in a trace.
   */
  @Nullable
  public final Long parentId;

  /**
   * Epoch microseconds of the start of this span, possibly absent if this an incomplete span.
   *
   * <p>This value should be set directly by instrumentation, using the most precise value
   * possible. For example, {@code gettimeofday} or multiplying {@link System#currentTimeMillis} by
   * 1000.
   *
   * <p>For compatibility with instrumentation that precede this field, collectors or span stores
   * can derive this via Annotation.timestamp. For example, {@link Constants#SERVER_RECV}.timestamp
   * or {@link Constants#CLIENT_SEND}.timestamp.
   *
   * <p>Timestamp is nullable for input only. Spans without a timestamp cannot be presented in a
   * timeline: Span stores should not output spans missing a timestamp.
   *
   * <p>There are two known edge-cases where this could be absent: both cases exist when a
   * collector receives a span in parts and a binary annotation precedes a timestamp. This is
   * possible when..
   * <ul>
   *   <li>The span is in-flight (ex not yet received a timestamp)</li>
   *   <li>The span's start event was lost</li>
   * </ul>
   */
  @Nullable
  public final Long timestamp;

  /**
   * Measurement in microseconds of the critical path, if known. Durations of less than one
   * microsecond must be rounded up to 1 microsecond.
   *
   * <p>This value should be set directly, as opposed to implicitly via annotation timestamps. Doing
   * so encourages precision decoupled from problems of clocks, such as skew or NTP updates causing
   * time to move backwards.
   *
   * <p>For compatibility with instrumentation that precede this field, collectors or span stores
   * can derive this by subtracting {@link Annotation#timestamp}. For example, {@link
   * Constants#SERVER_SEND}.timestamp - {@link Constants#SERVER_RECV}.timestamp.
   *
   * <p>If this field is persisted as unset, zipkin will continue to work, except duration query
   * support will be implementation-specific. Similarly, setting this field non-atomically is
   * implementation-specific.
   *
   * <p>This field is i64 vs i32 to support spans longer than 35 minutes.
   */
  @Nullable
  public final Long duration;

  /**
   * Associates events that explain latency with a timestamp.
   *
   * <p>Unlike log statements, annotations are often codes: for example {@link
   * Constants#SERVER_RECV}. Annotations are sorted ascending by timestamp.
   */
  public final List<Annotation> annotations;

  /**
   * Tags a span with context, usually to support query or aggregation.
   *
   * <p>example, a binary annotation key could be {@link TraceKeys#HTTP_PATH "http.path"}.
   */
  public final List<BinaryAnnotation> binaryAnnotations;

  /**
   * True is a request to store this span even if it overrides sampling policy.
   */
  @Nullable
  public final Boolean debug;

  Span(Builder builder) {
    this.traceId = checkNotNull(builder.traceId, "traceId");
    this.traceIdHigh = builder.traceIdHigh != null ? builder.traceIdHigh : 0L;
    this.name = checkNotNull(builder.name, "name").isEmpty() ? ""
        : builder.name.toLowerCase(Locale.ROOT);
    this.id = checkNotNull(builder.id, "id");
    this.parentId = builder.parentId;
    this.timestamp = builder.timestamp;
    this.duration = builder.duration;
    this.annotations = sortedList(builder.annotations);
    this.binaryAnnotations = sortedList(builder.binaryAnnotations);
    this.debug = builder.debug;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    Long traceId;
    Long traceIdHigh;
    String name;
    Long id;
    Long parentId;
    Long timestamp;
    Long duration;
    ArrayList<Annotation> annotations;
    ArrayList<BinaryAnnotation> binaryAnnotations;
    Boolean debug;
    boolean isClientSpan; // internal

    Builder() {
    }

    public Builder clear() {
      traceId = null;
      traceIdHigh = null;
      name = null;
      id = null;
      parentId = null;
      timestamp = null;
      duration = null;
      if (annotations != null) annotations.clear();
      if (binaryAnnotations != null) binaryAnnotations.clear();
      debug = null;
      isClientSpan = false;
      return this;
    }

    Builder(Span source) {
      this.traceId = source.traceId;
      this.traceIdHigh = source.traceIdHigh;
      this.name = source.name;
      this.id = source.id;
      this.parentId = source.parentId;
      this.timestamp = source.timestamp;
      this.duration = source.duration;
      if (!source.annotations.isEmpty()) {
        this.annotations(source.annotations);
      }
      if (!source.binaryAnnotations.isEmpty()) {
        this.binaryAnnotations(source.binaryAnnotations);
      }
      this.debug = source.debug;
    }

    public Builder merge(Span that) {
      if (this.traceId == null) {
        this.traceId = that.traceId;
      }
      if (this.traceIdHigh == null || this.traceIdHigh == 0) {
        this.traceIdHigh = that.traceIdHigh;
      }
      if (this.name == null || this.name.length() == 0 || this.name.equals("unknown")) {
        this.name = that.name;
      }
      if (this.id == null) {
        this.id = that.id;
      }
      if (this.parentId == null) {
        this.parentId = that.parentId;
      }

      // When we move to span model 2, remove this code in favor of using Span.kind == CLIENT
      boolean thisIsClientSpan = this.isClientSpan;
      boolean thatIsClientSpan = false;

      // This guards to ensure we don't add duplicate annotations or binary annotations on merge
      if (!that.annotations.isEmpty()) {
        boolean thisHadNoAnnotations = this.annotations == null;
        for (Annotation a : that.annotations) {
          if (a.value.equals(Constants.CLIENT_SEND)) thatIsClientSpan = true;
          if (thisHadNoAnnotations || !this.annotations.contains(a)) {
            addAnnotation(a);
          }
        }
      }

      if (!that.binaryAnnotations.isEmpty()) {
        boolean thisHadNoBinaryAnnotations = this.binaryAnnotations == null;
        for (BinaryAnnotation a : that.binaryAnnotations) {
          if (thisHadNoBinaryAnnotations || !this.binaryAnnotations.contains(a)) {
            addBinaryAnnotation(a);
          }
        }
      }

      // Single timestamp makes duration easy: just choose max
      if (this.timestamp == null || that.timestamp == null || this.timestamp.equals(
          that.timestamp)) {
        this.timestamp = this.timestamp != null ? this.timestamp : that.timestamp;
        if (this.duration == null) {
          this.duration = that.duration;
        } else if (that.duration != null) {
          this.duration = Math.max(this.duration, that.duration);
        }
      } else {
        // We have 2 different timestamps. If we have client data in either one of them, use that,
        // else set timestamp and duration to null
        if (thatIsClientSpan) {
          this.timestamp = that.timestamp;
          this.duration = that.duration;
        } else if (!thisIsClientSpan) {
          this.timestamp = null;
          this.duration = null;
        }
      }

      if (this.debug == null) {
        this.debug = that.debug;
      }
      return this;
    }

    /** @see Span#name */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /** @see Span#traceId */
    public Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }

    /** @see Span#traceIdHigh */
    public Builder traceIdHigh(long traceIdHigh) {
      this.traceIdHigh = traceIdHigh;
      return this;
    }

    /** @see Span#id */
    public Builder id(long id) {
      this.id = id;
      return this;
    }

    /** @see Span#parentId */
    public Builder parentId(@Nullable Long parentId) {
      this.parentId = parentId;
      return this;
    }

    /** @see Span#timestamp */
    public Builder timestamp(@Nullable Long timestamp) {
      this.timestamp = timestamp != null && timestamp == 0L ? null : timestamp;
      return this;
    }

    /** @see Span#duration */
    public Builder duration(@Nullable Long duration) {
      this.duration = duration != null && duration == 0L ? null : duration;
      return this;
    }

    /**
     * Replaces currently collected annotations.
     *
     * @see Span#annotations
     */
    public Builder annotations(Collection<Annotation> annotations) {
      if (this.annotations != null) this.annotations.clear();
      for (Annotation a : annotations) addAnnotation(a);
      return this;
    }

    /** @see Span#annotations */
    public Builder addAnnotation(Annotation annotation) {
      if (annotations == null) annotations = new ArrayList<>(4);
      if (annotation.value.equals(Constants.CLIENT_SEND)) isClientSpan = true;
      annotations.add(annotation);
      return this;
    }

    /**
     * Replaces currently collected binary annotations.
     *
     * @see Span#binaryAnnotations
     */
    public Builder binaryAnnotations(Collection<BinaryAnnotation> binaryAnnotations) {
      if (this.binaryAnnotations != null) this.binaryAnnotations.clear();
      for (BinaryAnnotation b : binaryAnnotations) addBinaryAnnotation(b);
      return this;
    }

    /** @see Span#binaryAnnotations */
    public Builder addBinaryAnnotation(BinaryAnnotation binaryAnnotation) {
      if (binaryAnnotations == null) binaryAnnotations = new ArrayList<>(4);
      binaryAnnotations.add(binaryAnnotation);
      return this;
    }

    /** @see Span#debug */
    public Builder debug(@Nullable Boolean debug) {
      this.debug = debug;
      return this;
    }

    public Span build() {
      return new Span(this);
    }
  }

  @Override
  public String toString() {
    return new String(Codec.JSON.writeSpan(this), UTF_8);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Span)) return false;
    Span that = (Span) o;
    return (this.traceIdHigh == that.traceIdHigh)
        && (this.traceId == that.traceId)
        && (this.name.equals(that.name))
        && (this.id == that.id)
        && equal(this.parentId, that.parentId)
        && equal(this.timestamp, that.timestamp)
        && equal(this.duration, that.duration)
        && (this.annotations.equals(that.annotations))
        && (this.binaryAnnotations.equals(that.binaryAnnotations))
        && equal(this.debug, that.debug);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) (h ^ ((traceIdHigh >>> 32) ^ traceIdHigh));
    h *= 1000003;
    h ^= (int) (h ^ ((traceId >>> 32) ^ traceId));
    h *= 1000003;
    h ^= name.hashCode();
    h *= 1000003;
    h ^= (int) (h ^ ((id >>> 32) ^ id));
    h *= 1000003;
    h ^= (parentId == null) ? 0 : parentId.hashCode();
    h *= 1000003;
    h ^= (timestamp == null) ? 0 : timestamp.hashCode();
    h *= 1000003;
    h ^= (duration == null) ? 0 : duration.hashCode();
    h *= 1000003;
    h ^= annotations.hashCode();
    h *= 1000003;
    h ^= binaryAnnotations.hashCode();
    h *= 1000003;
    h ^= (debug == null) ? 0 : debug.hashCode();
    return h;
  }

  /** Compares by {@link #timestamp}, then {@link #name}. */
  @Override
  public int compareTo(Span that) {
    if (this == that) return 0;
    long x = this.timestamp == null ? Long.MIN_VALUE : this.timestamp;
    long y = that.timestamp == null ? Long.MIN_VALUE : that.timestamp;
    int byTimestamp = x < y ? -1 : x == y ? 0 : 1;  // Long.compareTo is JRE 7+
    if (byTimestamp != 0) return byTimestamp;
    return this.name.compareTo(that.name);
  }

  /** Returns the hex representation of the span's trace ID */
  public String traceIdString() {
    if (traceIdHigh != 0) {
      char[] result = new char[32];
      writeHexLong(result, 0, traceIdHigh);
      writeHexLong(result, 16, traceId);
      return new String(result);
    }
    char[] result = new char[16];
    writeHexLong(result, 0, traceId);
    return new String(result);
  }

  /** Returns {@code $traceId.$spanId<:$parentId or $spanId} */
  public String idString() {
    int resultLength = (3 * 16) + 3; // 3 ids and the constant delimiters
    if (traceIdHigh != 0) resultLength += 16;
    char[] result = new char[resultLength];
    int pos = 0;
    if (traceIdHigh != 0) {
      writeHexLong(result, pos, traceIdHigh);
      pos += 16;
    }
    writeHexLong(result, pos, traceId);
    pos += 16;
    result[pos++] = '.';
    writeHexLong(result, pos, id);
    pos += 16;
    result[pos++] = '<';
    result[pos++] = ':';
    writeHexLong(result, pos, parentId != null ? parentId : id);
    return new String(result);
  }

  /** Returns the distinct {@link Endpoint#serviceName service names} that logged to this span. */
  public Set<String> serviceNames() {
    Set<String> result = new HashSet<>();
    for (Annotation a : annotations) {
      if (a.endpoint == null) continue;
      if (a.endpoint.serviceName.isEmpty()) continue;
      result.add(a.endpoint.serviceName);
    }
    for (BinaryAnnotation a : binaryAnnotations) {
      if (a.endpoint == null) continue;
      if (a.endpoint.serviceName.isEmpty()) continue;
      result.add(a.endpoint.serviceName);
    }
    return result;
  }

  // Since this is an immutable object, and we have thrift handy, defer to a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(Codec.THRIFT.writeSpan(this));
  }

  static final class SerializedForm implements Serializable {
    private static final long serialVersionUID = 0L;

    private final byte[] bytes;

    SerializedForm(byte[] bytes) {
      this.bytes = bytes;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return Codec.THRIFT.readSpan(bytes);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
