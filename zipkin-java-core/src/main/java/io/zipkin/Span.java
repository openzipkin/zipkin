/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import io.zipkin.internal.JsonCodec;
import io.zipkin.internal.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static io.zipkin.internal.Util.checkNotNull;
import static io.zipkin.internal.Util.equal;
import static io.zipkin.internal.Util.sortedList;

public final class Span implements Comparable<Span> {

  public final long traceId;

  public final String name;

  public final long id;

  @Nullable
  public final Long parentId;

  public final List<Annotation> annotations;

  public final List<BinaryAnnotation> binaryAnnotations;

  @Nullable
  public final Boolean debug;

  Span(
      long traceId,
      String name,
      long id,
      @Nullable Long parentId,
      Collection<Annotation> annotations,
      Collection<BinaryAnnotation> binaryAnnotations,
      @Nullable Boolean debug) {
    this.traceId = traceId;
    this.name = checkNotNull(name, "name");
    this.id = id;
    this.parentId = parentId;
    this.annotations = sortedList(annotations);
    this.binaryAnnotations = Collections.unmodifiableList(new ArrayList<>(binaryAnnotations));
    this.debug = debug;
  }

  public static final class Builder {
    private Long traceId;
    private String name;
    private Long id;
    private Long parentId;
    private LinkedHashSet<Annotation> annotations = new LinkedHashSet<>();
    private LinkedHashSet<BinaryAnnotation> binaryAnnotations = new LinkedHashSet<>();
    private Boolean debug;

    public Builder() {
    }

    public Builder(Span source) {
      this.traceId = source.traceId;
      this.name = source.name;
      this.id = source.id;
      this.parentId = source.parentId;
      this.annotations.addAll(source.annotations);
      this.binaryAnnotations.addAll(source.binaryAnnotations);
      this.debug = source.debug;
    }

    public Builder merge(Span that) {
      if (this.traceId == null) {
        this.traceId = that.traceId;
      }
      if (this.name == null) {
        this.name = that.name;
      }
      if (this.id == null) {
        this.id = that.id;
      }
      if (this.parentId == null) {
        this.parentId = that.parentId;
      }
      this.annotations.addAll(that.annotations);
      this.binaryAnnotations.addAll(that.binaryAnnotations);
      if (this.debug == null) {
        this.debug = that.debug;
      }
      return this;
    }

    public Span.Builder name(String name) {
      this.name = name;
      return this;
    }

    public Span.Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }


    public Span.Builder id(long id) {
      this.id = id;
      return this;
    }

    @Nullable
    public Span.Builder parentId(Long parentId) {
      this.parentId = parentId;
      return this;
    }

    public Span.Builder addAnnotation(Annotation annotation) {
      this.annotations.add(annotation);
      return this;
    }

    public Span.Builder addBinaryAnnotation(BinaryAnnotation binaryAnnotation) {
      this.binaryAnnotations.add(binaryAnnotation);
      return this;
    }

    @Nullable
    public Span.Builder debug(Boolean debug) {
      this.debug = debug;
      return this;
    }

    public Span build() {
      return new Span(this.traceId, this.name, this.id, this.parentId, this.annotations, this.binaryAnnotations, this.debug);
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
    h ^= annotations.hashCode();
    h *= 1000003;
    h ^= binaryAnnotations.hashCode();
    h *= 1000003;
    h ^= (debug == null) ? 0 : debug.hashCode();
    return h;
  }

  @Override
  public int compareTo(Span that) {
    if (this == that) {
      return 0;
    }
    return Long.compare(this.startTs(), that.startTs());
  }

  public long startTs() {
    return annotations.isEmpty() ? Long.MIN_VALUE : annotations.get(0).timestamp;
  }

  public Long endTs() {
    return annotations.isEmpty() ? null : annotations.get(annotations.size() - 1).timestamp;
  }
}
