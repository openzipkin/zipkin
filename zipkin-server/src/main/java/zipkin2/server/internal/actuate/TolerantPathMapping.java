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
package zipkin2.server.internal.actuate;

import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.PathMapping;
import com.linecorp.armeria.server.PathMappingContext;
import com.linecorp.armeria.server.PathMappingResult;
import com.linecorp.armeria.server.VirtualHost;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

final class TolerantPathMapping implements PathMapping {
  final PathMapping delegate;

  TolerantPathMapping(PathMapping delegate) {
    this.delegate = delegate;
  }

  @Override public PathMappingResult apply(PathMappingContext mappingCtx) {
    if (consumeTypes().isEmpty() || mappingCtx.consumeType() != null) {
      return delegate.apply(mappingCtx);
    }
    return delegate.apply(
      new OverrideConsumesPathMappingContext(mappingCtx, consumeTypes().get(0)));
  }

  @Override public Set<String> paramNames() {
    return delegate.paramNames();
  }

  @Override public String loggerName() {
    return delegate.loggerName();
  }

  @Override public String meterTag() {
    return delegate.meterTag();
  }

  @Override public Optional<String> exactPath() {
    return delegate.exactPath();
  }

  @Override public Optional<String> prefix() {
    return delegate.prefix();
  }

  @Override public Optional<String> triePath() {
    return delegate.triePath();
  }

  @Override public Optional<String> regex() {
    return delegate.regex();
  }

  @Override public int complexity() {
    return delegate.complexity();
  }

  @Override public Set<HttpMethod> supportedMethods() {
    return delegate.supportedMethods();
  }

  @Override public List<MediaType> consumeTypes() {
    return delegate.consumeTypes();
  }

  @Override public List<MediaType> produceTypes() {
    return delegate.produceTypes();
  }

  @Override public PathMapping withHttpHeaderInfo(Set<HttpMethod> supportedMethods,
    List<MediaType> consumeTypes, List<MediaType> produceTypes) {
    return new TolerantPathMapping(
      delegate.withHttpHeaderInfo(supportedMethods, consumeTypes, produceTypes));
  }

  static final class OverrideConsumesPathMappingContext implements PathMappingContext {
    final PathMappingContext delegate;
    final MediaType consumeType;

    OverrideConsumesPathMappingContext(PathMappingContext delegate, MediaType consumeType) {
      this.delegate = delegate;
      this.consumeType = consumeType;
    }

    @Override public VirtualHost virtualHost() {
      return delegate.virtualHost();
    }

    @Override public String hostname() {
      return delegate.hostname();
    }

    @Override public HttpMethod method() {
      return delegate.method();
    }

    @Override public String path() {
      return delegate.path();
    }

    @Nullable @Override public String query() {
      return delegate.query();
    }

    @Nullable @Override public MediaType consumeType() {
      return consumeType;
    }

    @Nullable @Override public List<MediaType> produceTypes() {
      return delegate.produceTypes();
    }

    @Override public List<Object> summary() {
      return delegate.summary();
    }

    @Override public void delayThrowable(Throwable cause) {
      delegate.delayThrowable(cause);
    }

    @Override public Optional<Throwable> delayedThrowable() {
      return delegate.delayedThrowable();
    }

    @Override public PathMappingContext overridePath(String path) {
      return new OverrideConsumesPathMappingContext(delegate.overridePath(path), consumeType);
    }

    @Override public boolean isCorsPreflight() {
      return delegate.isCorsPreflight();
    }
  }
}
