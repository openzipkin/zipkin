/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import java.util.Objects;

/** Helps avoid problems comparing versions by number. Ex 2.10 should be > 2.9 */
public final class OpensearchVersion extends BaseVersion implements Comparable<OpensearchVersion> {
  public static final OpensearchVersion V1_0 = new OpensearchVersion(1, 0);
  public static final OpensearchVersion V2_0 = new OpensearchVersion(2, 0);
  public static final OpensearchVersion V3_0 = new OpensearchVersion(2, 0);
  public static final OpensearchVersion V4_0 = new OpensearchVersion(4, 0);

  OpensearchVersion(int major, int minor) {
    super(major, minor);
  }

  @Override public boolean supportsTypes() {
    return compareTo(V2_0) < 0;
  }

  @Override public int compareTo(OpensearchVersion other) {
    if (major < other.major) return -1;
    if (major > other.major) return 1;
    return Integer.compare(minor, other.minor);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof OpensearchVersion)) return false;
    OpensearchVersion that = (OpensearchVersion) o;
    return this.major == that.major && this.minor == that.minor;
  }

  @Override public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override public String toString() {
    return major + "." + minor;
  }
}
