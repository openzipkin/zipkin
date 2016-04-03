/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

package zipkin.scribe;

import com.facebook.swift.service.ThriftServerConfig;

import static zipkin.internal.Util.checkNotNull;

/** Configuration including defaults needed to receive spans from a Scribe category. */
public final class ScribeConfig {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String category = "zipkin";
    private int port = 9410;

    /** Category zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder category(String category) {
      this.category = category;
      return this;
    }

    /** The port to listen on. Defaults to 9410 */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public ScribeConfig build() {
      return new ScribeConfig(this);
    }
  }

  final String category;
  final int port;

  ScribeConfig(Builder builder) {
    this.category = checkNotNull(builder.category, "category");
    this.port = builder.port;
  }

  ThriftServerConfig forThriftServer() {
    return new ThriftServerConfig().setPort(port);
  }
}
