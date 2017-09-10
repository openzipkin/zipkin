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
package zipkin2;

import com.google.auto.value.AutoValue;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Locale;
import javax.annotation.concurrent.Immutable;
import zipkin2.codec.DependencyLinkBytesEncoder;

@Immutable
@AutoValue
public abstract class DependencyLink implements Serializable { // for Spark jobs
  static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final long serialVersionUID = 0L;

  public static Builder newBuilder() {
    return new AutoValue_DependencyLink.Builder().errorCount(0);
  }

  /** parent service name (caller) */
  public abstract String parent();

  /** child service name (callee) */
  public abstract String child();

  /** total traced calls made from {@link #parent} to {@link #child} */
  public abstract long callCount();

  /** How many {@link #callCount calls} are known to be errors */
  public abstract long errorCount();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {

    public abstract Builder parent(String parent);

    public abstract Builder child(String child);

    public abstract Builder callCount(long callCount);

    public abstract Builder errorCount(long errorCount);

    abstract String parent();

    abstract String child();

    abstract DependencyLink autoBuild();

    public final DependencyLink build() {
      return parent(parent().toLowerCase(Locale.ROOT))
        .child(child().toLowerCase(Locale.ROOT)).autoBuild();
    }

    Builder() {
    }
  }

  @Override public String toString() {
    return new String(DependencyLinkBytesEncoder.JSON_V1.encode(this), UTF_8);
  }

  DependencyLink() {
  }
}
