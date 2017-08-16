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
import java.util.Locale;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkNotNull;

public final class DependencyLink implements Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  /** @deprecated please use {@link #builder()} */
  public static DependencyLink create(String parent, String child, long callCount) {
    return builder().parent(parent).child(child).callCount(callCount).build();
  }

  /** parent service name (caller) */
  public final String parent;

  /** child service name (callee) */
  public final String child;

  /** total traced calls made from {@link #parent} to {@link #child} */
  public final long callCount;

  /** How many {@link #callCount calls} are known to be {@link Constants#ERROR errors} */
  public final long errorCount;

  DependencyLink(Builder builder) {
    parent = checkNotNull(builder.parent, "parent").toLowerCase(Locale.ROOT);
    child = checkNotNull(builder.child, "child").toLowerCase(Locale.ROOT);
    callCount = builder.callCount;
    errorCount = builder.errorCount;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String parent;
    private String child;
    private long callCount;
    private long errorCount;

    Builder() {
    }

    Builder(DependencyLink source) {
      this.parent = source.parent;
      this.child = source.child;
      this.callCount = source.callCount;
      this.errorCount = source.errorCount;
    }

    public Builder parent(String parent) {
      this.parent = parent;
      return this;
    }

    public Builder child(String child) {
      this.child = child;
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
      return new DependencyLink(this);
    }
  }

  @Override
  public String toString() {
    return new String(Codec.JSON.writeDependencyLink(this), UTF_8);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof DependencyLink) {
      DependencyLink that = (DependencyLink) o;
      return (this.parent.equals(that.parent))
          && (this.child.equals(that.child))
          && (this.callCount == that.callCount)
          && (this.errorCount == that.errorCount);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= parent.hashCode();
    h *= 1000003;
    h ^= child.hashCode();
    h *= 1000003;
    h ^= (int) (h ^ ((callCount >>> 32) ^ callCount));
    h *= 1000003;
    h ^= (int) (h ^ ((errorCount >>> 32) ^ errorCount));
    return h;
  }

  // Since this is an immutable object, and we have thrift handy, defer to a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(Codec.THRIFT.writeDependencyLink(this));
  }

  static final class SerializedForm implements Serializable {
    private static final long serialVersionUID = 0L;

    private final byte[] bytes;

    SerializedForm(byte[] bytes) {
      this.bytes = bytes;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return Codec.THRIFT.readDependencyLink(bytes);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
