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

import io.zipkin.internal.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.zipkin.internal.Util.checkArgument;

public final class QueryRequest {

  /** Only include traces whose annotation includes this {@link io.zipkin.Endpoint#serviceName} */
  public final String serviceName;

  /** When present, only include traces with this {@link io.zipkin.Span#name} */
  @Nullable
  public final String spanName;

  /**
   * Include traces whose {@link io.zipkin.Span#annotations} include a value in this set.
   *
   * <p/> This is an AND condition against the set, as well against {@link #binaryAnnotations}
   */
  public final List<String> annotations;

  /**
   * Include traces whose {@link io.zipkin.Span#binaryAnnotations} include a String whose key and
   * value are an entry in this set.
   *
   * <p/> This is an AND condition against the set, as well against {@link #annotations}
   */
  public final Map<String, String> binaryAnnotations;

  /**
   * Only return traces where all {@link io.zipkin.Span#endTs} are at or before this time in epoch
   * microseconds. Defaults to current time.
   */
  public final long endTs;

  /** Maximum number of traces to return. Defaults to 10 */
  public final int limit;

  private QueryRequest(
      String serviceName,
      String spanName,
      List<String> annotations,
      Map<String, String> binaryAnnotations,
      long endTs,
      int limit) {
    checkArgument(serviceName != null && !serviceName.isEmpty(), "serviceName was empty");
    checkArgument(spanName == null || !spanName.isEmpty(), "spanName was empty");
    checkArgument(endTs > 0, "endTs should be positive, in epoch microseconds: was %d", endTs);
    checkArgument(limit > 0, "limit should be positive: was %d", limit);
    this.serviceName = serviceName;
    this.spanName = spanName;
    this.annotations = annotations;
    this.binaryAnnotations = binaryAnnotations;
    this.endTs = endTs;
    this.limit = limit;
  }

  public static final class Builder {

    private String serviceName;
    private String spanName;
    private List<String> annotations = Collections.emptyList();
    private Map<String, String> binaryAnnotations = Collections.emptyMap();
    private Long endTs;
    private Integer limit;

    public Builder() {
    }

    public Builder(QueryRequest source) {
      this.serviceName = source.serviceName;
      this.spanName = source.spanName;
      this.annotations = source.annotations;
      this.binaryAnnotations = source.binaryAnnotations;
      this.endTs = source.endTs;
      this.limit = source.limit;
    }

    public QueryRequest.Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public QueryRequest.Builder spanName(@Nullable String spanName) {
      this.spanName = spanName;
      return this;
    }

    public QueryRequest.Builder annotations(List<String> annotations) {
      this.annotations = annotations;
      return this;
    }

    public QueryRequest.Builder binaryAnnotations(Map<String, String> binaryAnnotations) {
      this.binaryAnnotations = binaryAnnotations;
      return this;
    }

    public QueryRequest.Builder endTs(long endTs) {
      this.endTs = endTs;
      return this;
    }

    public QueryRequest.Builder limit(int limit) {
      this.limit = limit;
      return this;
    }

    public QueryRequest build() {
      return new QueryRequest(
          serviceName,
          spanName,
          annotations,
          binaryAnnotations,
          endTs == null ? System.currentTimeMillis() * 1000 : endTs,
          limit == null ? 10 : limit);
    }
  }

  @Override
  public String toString() {
    return "QueryRequest{"
        + "serviceName=" + serviceName + ", "
        + "spanName=" + spanName + ", "
        + "annotations=" + annotations + ", "
        + "binaryAnnotations=" + binaryAnnotations + ", "
        + "endTs=" + endTs + ", "
        + "limit=" + limit
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof QueryRequest) {
      QueryRequest that = (QueryRequest) o;
      return (this.serviceName.equals(that.serviceName))
          && ((this.spanName == null) ? (that.spanName == null) : this.spanName.equals(that.spanName))
          && ((this.annotations == null) ? (that.annotations == null) : this.annotations.equals(that.annotations))
          && ((this.binaryAnnotations == null) ? (that.binaryAnnotations == null) : this.binaryAnnotations.equals(that.binaryAnnotations))
          && (this.endTs == that.endTs)
          && (this.limit == that.limit);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= serviceName.hashCode();
    h *= 1000003;
    h ^= (spanName == null) ? 0 : spanName.hashCode();
    h *= 1000003;
    h ^= (annotations == null) ? 0 : annotations.hashCode();
    h *= 1000003;
    h ^= (binaryAnnotations == null) ? 0 : binaryAnnotations.hashCode();
    h *= 1000003;
    h ^= (endTs >>> 32) ^ endTs;
    h *= 1000003;
    h ^= limit;
    return h;
  }
}
