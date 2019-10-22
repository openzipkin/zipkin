/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.charset.Charset;
import java.util.Locale;
import zipkin2.codec.DependencyLinkBytesDecoder;
import zipkin2.codec.DependencyLinkBytesEncoder;

//@Immutable
public final class DependencyLink implements Serializable { // for Spark and Flink jobs
  static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final long serialVersionUID = 0L;

  public static Builder newBuilder() {
    return new Builder();
  }

  /** parent service name (caller) */
  public String parent() {
    return parent;
  }

  /** child service name (callee) */
  public String child() {
    return child;
  }

  /** total traced calls made from {@link #parent} to {@link #child} */
  public long callCount() {
    return callCount;
  }

  /** How many {@link #callCount calls} are known to be errors */
  public long errorCount() {
    return errorCount;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {
    String parent, child;
    long callCount, errorCount;

    Builder() {
    }

    Builder(DependencyLink source) {
      this.parent = source.parent;
      this.child = source.child;
      this.callCount = source.callCount;
      this.errorCount = source.errorCount;
    }

    public Builder parent(String parent) {
      if (parent == null) throw new NullPointerException("parent == null");
      this.parent = parent.toLowerCase(Locale.ROOT);
      return this;
    }

    public Builder child(String child) {
      if (child == null) throw new NullPointerException("child == null");
      this.child = child.toLowerCase(Locale.ROOT);
      return this;
    }

    public Builder callCount(long callCount) {
      this.callCount = callCount;
      return this;
    }

    public Builder errorCount(long errorCount) {
      this.errorCount = errorCount;
      return this;
    }

    public DependencyLink build() {
      String missing = "";
      if (parent == null) missing += " parent";
      if (child == null) missing += " child";
      if (!"".equals(missing)) throw new IllegalStateException("Missing :" + missing);
      return new DependencyLink(this);
    }
  }

  @Override public String toString() {
    return new String(DependencyLinkBytesEncoder.JSON_V1.encode(this), UTF_8);
  }

  // clutter below mainly due to difficulty working with Kryo which cannot handle AutoValue subclass
  // See https://github.com/openzipkin/zipkin/issues/1879
  final String parent, child;
  final long callCount, errorCount;

  DependencyLink(Builder builder) {
    parent = builder.parent;
    child = builder.child;
    callCount = builder.callCount;
    errorCount = builder.errorCount;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DependencyLink)) return false;
    DependencyLink that = (DependencyLink) o;
    return parent.equals(that.parent)
      && child.equals(that.child)
      && callCount == that.callCount
      && errorCount == that.errorCount;
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= parent.hashCode();
    h *= 1000003;
    h ^= child.hashCode();
    h *= 1000003;
    h ^= (int) ((callCount >>> 32) ^ callCount);
    h *= 1000003;
    h ^= (int) ((errorCount >>> 32) ^ errorCount);
    return h;
  }

  // This is an immutable object, and our encoder is faster than java's: use a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(DependencyLinkBytesEncoder.JSON_V1.encode(this));
  }

  private static final class SerializedForm implements Serializable {
    private static final long serialVersionUID = 0L;

    final byte[] bytes;

    SerializedForm(byte[] bytes) {
      this.bytes = bytes;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return DependencyLinkBytesDecoder.JSON_V1.decodeOne(bytes);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
