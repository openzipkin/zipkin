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
package zipkin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import zipkin.internal.JsonCodec;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.equal;
import static zipkin.internal.Util.sortedList;

/**
 * A trace is a series of spans (often RPC calls) which form a latency tree.
 *
 * <p/>Spans are usually created by instrumentation in RPC clients or servers, but can also
 * represent in-process activity. Annotations in spans are similar to log statements, and are
 * sometimes created directly by application developers to indicate events of interest, such as a
 * cache miss.
 *
 * <p/>The root span is where {@link #parentId} is null; it usually has the longest {@link #duration} in the
 * trace.
 *
 * <p/>Span identifiers are packed into longs, but should be treated opaquely. String encoding is
 * fixed-width lower-hex, to avoid signed interpretation.
 */
public final class Span implements Comparable<Span> {
  /**
   * Unique 8-byte identifier for a trace, set on all spans within it.
   */
  public final long traceId;

  /**
   * Span name in lowercase, rpc method for example.
   *
   * <p/>Conventionally, when the span name isn't known, name = "unknown".
   */
  public final String name;

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p/>A span is uniquely identified in storage by ({@linkplain #traceId}, {@code #id}).
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
   * <p/>This value should be set directly by instrumentation, using the most precise value
   * possible. For example, {@code gettimeofday} or syncing {@link System#nanoTime} against a tick
   * of {@link System#currentTimeMillis}.
   *
   * <p/>For compatibilty with instrumentation that precede this field, collectors or span stores
   * can derive this via Annotation.timestamp. For example, {@link Constants#SERVER_RECV}.timestamp
   * or {@link Constants#CLIENT_SEND}.timestamp.
   *
   * <p/>Timestamp is nullable for input only. Spans without a timestamp cannot be presented in a
   * timeline: Span stores should not output spans missing a timestamp.
   *
   * <p/>There are two known edge-cases where this could be absent: both cases exist when a
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
   * Measurement in microseconds of the critical path, if known.
   *
   * <p/>This value should be set directly, as opposed to implicitly via annotation timestamps.
   * Doing so encourages precision decoupled from problems of clocks, such as skew or NTP updates
   * causing time to move backwards.
   *
   * <p/>For compatibility with instrumentation that precede this field, collectors or span stores
   * can derive this by subtracting {@link Annotation#timestamp}. For example, {@link
   * Constants#SERVER_SEND}.timestamp - {@link Constants#SERVER_RECV}.timestamp.
   *
   * <p/>If this field is persisted as unset, zipkin will continue to work, except duration query
   * support will be implementation-specific. Similarly, setting this field non-atomically is
   * implementation-specific.
   *
   * <p/>This field is i64 vs i32 to support spans longer than 35 minutes.
   */
  @Nullable
  public final Long duration;

  /**
   * Associates events that explain latency with a timestamp.
   *
   * <p/>Unlike log statements, annotations are often codes: for example {@link
   * Constants#SERVER_RECV}. Annotations are sorted ascending by timestamp.
   */
  public final List<Annotation> annotations;

  /**
   * Tags a span with context, usually to support query or aggregation.
   *
   * <p/>example, a binary annotation key could be "http.uri".
   */
  public final List<BinaryAnnotation> binaryAnnotations;

  /**
   * True is a request to store this span even if it overrides sampling policy.
   */
  @Nullable
  public final Boolean debug;

  Span(long traceId,
       String name,
       long id,
       @Nullable Long parentId,
       @Nullable Long timestamp,
       @Nullable Long duration,
       Collection<Annotation> annotations,
       Collection<BinaryAnnotation> binaryAnnotations,
       @Nullable Boolean debug) {
    this.traceId = traceId;
    this.name = checkNotNull(name, "name").toLowerCase();
    this.id = id;
    this.parentId = parentId;
    this.timestamp = timestamp;
    this.duration = duration;
    this.annotations = sortedList(annotations);
    this.binaryAnnotations = Collections.unmodifiableList(new ArrayList<>(binaryAnnotations));
    this.debug = debug;
  }

  public static final class Builder {
    private Long traceId;
    private String name;
    private Long id;
    private Long parentId;
    private Long timestamp;
    private Long duration;
    private final TreeSet<Annotation> annotations = new TreeSet<>();
    private final LinkedHashSet<BinaryAnnotation> binaryAnnotations = new LinkedHashSet<>();
    private Boolean debug;

    public Builder() {
    }

    public Builder(Span source) {
      this.traceId = source.traceId;
      this.name = source.name;
      this.id = source.id;
      this.parentId = source.parentId;
      this.timestamp = source.timestamp;
      this.duration = source.duration;
      this.annotations.addAll(source.annotations);
      this.binaryAnnotations.addAll(source.binaryAnnotations);
      this.debug = source.debug;
    }

    public Builder merge(Span that) {
      if (this.traceId == null) {
        this.traceId = that.traceId;
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

      // Single timestamp makes duration easy: just choose max
      if (this.timestamp == null || that.timestamp == null || this.timestamp.equals(that.timestamp)) {
        this.timestamp = this.timestamp != null ? this.timestamp : that.timestamp;
        if (this.duration == null) {
          this.duration = that.duration;
        } else if (that.duration != null) {
          this.duration = Math.max(this.duration, that.duration);
        }
      } else { // duration might need to be recalculated, since we have 2 different timestamps
        long thisEndTs = this.duration != null ? this.timestamp + this.duration : this.timestamp;
        long thatEndTs = that.duration != null ? that.timestamp + that.duration : that.timestamp;
        this.timestamp = Math.min(this.timestamp, that.timestamp);
        this.duration = Math.max(thisEndTs, thatEndTs) - this.timestamp;
      }

      this.annotations.addAll(that.annotations);
      this.binaryAnnotations.addAll(that.binaryAnnotations);
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
      this.timestamp = timestamp;
      return this;
    }

    /** @see Span#duration */
    public Builder duration(@Nullable Long duration) {
      this.duration = duration;
      return this;
    }

    /**
     * Replaces currently collected annotations.
     *
     * @see Span#annotations
     */
    public Builder annotations(Annotation... annotations) {
      this.annotations.clear();
      Collections.addAll(this.annotations, annotations);
      return this;
    }

    /** @see Span#annotations */
    public Builder addAnnotation(Annotation annotation) {
      this.annotations.add(annotation);
      return this;
    }

    /**
     * Replaces currently collected binary annotations.
     *
     * @see Span#binaryAnnotations
     */
    public Builder binaryAnnotations(BinaryAnnotation... binaryAnnotations) {
      this.binaryAnnotations.clear();
      Collections.addAll(this.binaryAnnotations, binaryAnnotations);
      return this;
    }

    /** @see Span#binaryAnnotations */
    public Builder addBinaryAnnotation(BinaryAnnotation binaryAnnotation) {
      this.binaryAnnotations.add(binaryAnnotation);
      return this;
    }

    /** @see Span#debug */
    public Builder debug(@Nullable Boolean debug) {
      this.debug = debug;
      return this;
    }

    public Span build() {
      return new Span(this.traceId, this.name, this.id, this.parentId, this.timestamp, this.duration, this.annotations, this.binaryAnnotations, this.debug);
    }
  }

  @Override
  public String toString() {
    return JsonCodec.SPAN_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Span) {
      Span that = (Span) o;
      return (this.traceId == that.traceId)
          && (this.name.equals(that.name))
          && (this.id == that.id)
          && equal(this.parentId, that.parentId)
          && equal(this.timestamp, that.timestamp)
          && equal(this.duration, that.duration)
          && (this.annotations.equals(that.annotations))
          && (this.binaryAnnotations.equals(that.binaryAnnotations))
          && equal(this.debug, that.debug);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (traceId >>> 32) ^ traceId;
    h *= 1000003;
    h ^= name.hashCode();
    h *= 1000003;
    h ^= (id >>> 32) ^ id;
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
    int byTimestamp = Long.compare(
        this.timestamp == null ? Long.MIN_VALUE : this.timestamp,
        that.timestamp == null ? Long.MIN_VALUE : that.timestamp);
    if (byTimestamp != 0) return byTimestamp;
    return this.name.compareTo(that.name);
  }
}
