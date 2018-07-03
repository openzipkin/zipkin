/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage;

import zipkin2.internal.Nullable;

public final class DependencyQueryRequest {

  public Builder toBuilder() { return new Builder(this); }

  public static Builder newBuilder() { return new Builder(); }

  public static final class Builder {

    private String parentServiceName;
    private String childServiceName;
    private long endTs, lookback;
    private int limit;
    private boolean errorsOnly;

    Builder() {}

    Builder(DependencyQueryRequest source) {
      parentServiceName = source.parentServiceName;
      childServiceName = source.childServiceName;
      endTs = source.endTs;
      lookback = source.lookback;
      limit = source.limit;
      errorsOnly = source.errorsOnly;
    }

    public Builder parentServiceName(String parentServiceName) {
      this.parentServiceName = parentServiceName;
      return this;
    }

    public Builder childServiceName(String childServiceName) {
      this.childServiceName = childServiceName;
      return this;
    }

    public Builder endTs(long endTs) {
      this.endTs = endTs;
      return this;
    }

    public Builder lookback(long lookback) {
      this.lookback = lookback;
      return this;
    }

    public Builder limit(int limit) {
      this.limit = limit;
      return this;
    }

    public Builder errorsOnly(boolean errorsOnly) {
      this.errorsOnly = errorsOnly;
      return this;
    }

    public final DependencyQueryRequest build() {
      if ("".equals(parentServiceName)) throw new IllegalArgumentException("parentServiceName == ''");
      if ("".equals(childServiceName)) throw new IllegalArgumentException("childServiceName == ''");
      if (parentServiceName == null) throw new IllegalArgumentException("parentServiceName == null");
      if (childServiceName == null) throw new IllegalArgumentException("childServiceName == null");
      if (endTs <= 0) throw new IllegalArgumentException("endTs <= 0");
      if (limit <= 0) throw new IllegalArgumentException("limit <= 0");
      if (lookback <= 0) throw new IllegalArgumentException("lookback <= 0");

      return new DependencyQueryRequest(
        parentServiceName,
        childServiceName,
        endTs,
        lookback,
        limit,
        errorsOnly
      );
    }
  }

  public final String parentServiceName;
  public final String childServiceName;
  public final long endTs, lookback;
  public final int limit;
  public final Boolean errorsOnly;

  private DependencyQueryRequest(
    String parentServiceName,
    String childServiceName,
    long endTs,
    long lookback,
    int limit,
    @Nullable Boolean errorsOnly
  ) {
    this.parentServiceName = parentServiceName;
    this.childServiceName = childServiceName;
    this.endTs = endTs;
    this.lookback = lookback;
    this.limit = limit;
    this.errorsOnly = errorsOnly;
  }

  @Override
  public String toString() {
    return "DependencyQueryRequest{"
      + "parentServiceName=" + parentServiceName + ", "
      + "childServiceName=" + childServiceName + ", "
      + "endTs=" + endTs + ", "
      + "lookback=" + lookback + ", "
      + "limit=" + limit + ", "
      + "errorsOnly=" + errorsOnly
      + "}";
  }

}
