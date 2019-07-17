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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.DecoratingClientFunction;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.concurrent.CompletionException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.isA;

public class BasicAuthInterceptorTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override public void configure(ServerBuilder sb) {
      sb.service("/", (ctx, req) -> HttpResponse.of(AggregatedHttpResponse.of(
        HttpStatus.FORBIDDEN, MediaType.JSON_UTF_8, "{\"message\":\"Sadness.\"}")));
    }
  };

  HttpClient client;

  @Before public void setUp() {
    client = new HttpClientBuilder(server.httpUri("/"))
      .decorator((delegate, ctx, req) -> new BasicAuthInterceptor(delegate,
        new ZipkinElasticsearchStorageProperties(false, 0)).execute(ctx, req)).build();
  }

  @Test public void intercept_whenESReturns403AndJsonBody_throwsWithResponseBodyMessage() {
    thrown.expect(CompletionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    thrown.expectMessage("Sadness.");

    client.get("/").aggregate().join();
  }
}
