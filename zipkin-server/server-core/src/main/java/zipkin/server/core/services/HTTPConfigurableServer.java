/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.core.services;

import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.server.ServerBuilder;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;

import java.util.List;

public class HTTPConfigurableServer extends HTTPServer {
  public HTTPConfigurableServer(HTTPServerConfig config) {
    super(config);
  }

  @Override
  public void addHandler(Object handler, List<HttpMethod> httpMethods) {
    if (handler instanceof ServerConfiguration) {
      ((ServerConfiguration) handler).configure(sb);
      allowedMethods.addAll(httpMethods);
      return;
    }
    super.addHandler(handler, httpMethods);
  }

  public interface ServerConfiguration {
    void configure(ServerBuilder builder);
  }
}
