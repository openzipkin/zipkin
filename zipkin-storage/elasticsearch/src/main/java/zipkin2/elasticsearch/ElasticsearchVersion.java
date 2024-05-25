/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import java.util.Objects;

/** Helps avoid problems comparing versions by number. Ex 7.10 should be > 7.9 */
public final class ElasticsearchVersion extends BaseVersion implements Comparable<ElasticsearchVersion> {
  public static final ElasticsearchVersion V5_0 = new ElasticsearchVersion(5, 0);
  public static final ElasticsearchVersion V6_0 = new ElasticsearchVersion(6, 0);
  public static final ElasticsearchVersion V6_7 = new ElasticsearchVersion(6, 7);
  public static final ElasticsearchVersion V7_0 = new ElasticsearchVersion(7, 0);
  public static final ElasticsearchVersion V7_8 = new ElasticsearchVersion(7, 8);
  public static final ElasticsearchVersion V9_0 = new ElasticsearchVersion(9, 0);

  ElasticsearchVersion(int major, int minor) {
    super(major, minor);
  }

  @Override public boolean supportsTypes() {
    return compareTo(V7_0) < 0;
  }

  @Override public int compareTo(ElasticsearchVersion other) {
    if (major < other.major) return -1;
    if (major > other.major) return 1;
    return Integer.compare(minor, other.minor);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ElasticsearchVersion)) return false;
    ElasticsearchVersion that = (ElasticsearchVersion) o;
    return this.major == that.major && this.minor == that.minor;
  }

  @Override public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override public String toString() {
    return major + "." + minor;
  }


}
