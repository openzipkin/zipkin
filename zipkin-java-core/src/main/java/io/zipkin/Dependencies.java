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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/* An aggregate representation of services paired with every service they call. */

public final class Dependencies {

  /** microseconds from epoch */
  public final long startTs;

  /** microseconds from epoch */
  public final long endTs;

  public final List<DependencyLink> links;

  Dependencies(long startTs, long endTs, List<DependencyLink> links) {
    this.startTs = startTs;
    this.endTs = endTs;
    this.links = Collections.unmodifiableList(new ArrayList<>(links));
  }

  @Override
  public String toString() {
    return JsonCodec.DEPENDENCIES_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Dependencies) {
      Dependencies that = (Dependencies) o;
      return (this.startTs == that.startTs)
          && (this.endTs == that.endTs)
          && (this.links.equals(that.links));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (startTs >>> 32) ^ startTs;
    h *= 1000003;
    h ^= (endTs >>> 32) ^ endTs;
    h *= 1000003;
    h ^= links.hashCode();
    return h;
  }

  public static final class Builder {
    private long startTs;
    private long endTs;
    private List<DependencyLink> links = new LinkedList<>();

    public Builder() {
    }

    public Builder(Dependencies source) {
      this.startTs = source.startTs;
      this.endTs = source.endTs;
      this.links = source.links;
    }

    public Builder startTs(long startTs) {
      this.startTs = startTs;
      return this;
    }

    public Builder endTs(long endTs) {
      this.endTs = endTs;
      return this;
    }

    public Builder addLink(DependencyLink link) {
      this.links.add(link);
      return this;
    }

    public Dependencies build() {
      return new Dependencies(this.startTs, this.endTs, this.links);
    }
  }
}
