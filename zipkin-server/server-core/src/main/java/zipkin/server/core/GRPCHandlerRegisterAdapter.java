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
package zipkin.server.core;

import com.linecorp.armeria.common.HttpMethod;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;

import java.util.Arrays;

public class GRPCHandlerRegisterAdapter implements GRPCHandlerRegister {

    private final HTTPServer server;

    public GRPCHandlerRegisterAdapter(HTTPServer server) {
        this.server = server;
    }

    @Override
    public void addHandler(BindableService handler) {
        server.addHandler(handler, Arrays.asList(HttpMethod.GET));
    }

    @Override
    public void addHandler(ServerServiceDefinition definition) {
        server.addHandler(definition, Arrays.asList(HttpMethod.GET));
    }

    @Override
    public void addFilter(ServerInterceptor interceptor) {
        server.addHandler(interceptor, Arrays.asList(HttpMethod.GET));
    }
}
