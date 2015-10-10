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
import io.zipkin.internal.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An aggregate representation of services paired with every service they call. */
public final class Dependencies {
  public static final Dependencies ZERO = new Dependencies(0, 0, Collections.<DependencyLink>emptyList());

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

  /** used for summing/merging database rows */
  public Dependencies merge(Dependencies that) {
    // don't sum against Dependencies.ZERO
    if (that == Dependencies.ZERO) {
      return this;
    } else if (this == Dependencies.ZERO) {
      return that;
    }

    // new start/end should be the inclusive time span of both items
    Dependencies.Builder result = new Dependencies.Builder()
        .startTs(Long.min(startTs, that.startTs))
        .endTs(Long.max(endTs, that.endTs));

    for (int i = 0, length = this.links.size(); i < length; i++) {
      result.addLink(this.links.get(i));
    }
    for (int i = 0, length = that.links.size(); i < length; i++) {
      result.addLink(that.links.get(i));
    }
    return result.build();
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
    // links are merged by mapping to parent/child and summing corresponding links
    private Map<Pair<String>, Long> linkMap = new LinkedHashMap<>();

    public Builder() {
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
      Pair key = Pair.create(link.parent, link.child);
      if (linkMap.containsKey(key)) {
        linkMap.put(key, linkMap.get(key) + link.callCount);
      } else {
        linkMap.put(key, link.callCount);
      }
      return this;
    }

    public Dependencies build() {
      List<DependencyLink> links = new ArrayList<>(linkMap.size());
      for (Map.Entry<Pair<String>, Long> entry : linkMap.entrySet()) {
        links.add(new DependencyLink(entry.getKey()._1, entry.getKey()._2, entry.getValue()));
      }
      return new Dependencies(this.startTs, this.endTs, links);
    }
  }
}
