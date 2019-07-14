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
package zipkin2.server.internal.elasticsearch

import com.linecorp.armeria.client.Client
import com.linecorp.armeria.client.HttpClient
import com.linecorp.armeria.client.HttpClientBuilder
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.server.ServerBuilder
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.testing.junit4.server.ServerRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CompletionException

class BasicAuthInterceptorTest {

  companion object {
    @ClassRule @JvmField val server = object: ServerRule() {
      override fun configure(sb: ServerBuilder) {
        sb.service("/") { _: ServiceRequestContext, _: HttpRequest ->
          HttpResponse.of(AggregatedHttpResponse.of(
            HttpStatus.FORBIDDEN, MediaType.JSON_UTF_8, "{\"message\":\"Sadness.\"}"))
        }
      }
    }
  }

  @Before
  fun setUp() {
    client = HttpClientBuilder(server.httpUri("/"))
      .decorator{ client: Client<HttpRequest, HttpResponse> ->
        BasicAuthInterceptor(client, ZipkinElasticsearchStorageProperties(false, 0))
      }
      .build()
  }

  var client: HttpClient? = null

  @Test fun intercept_whenESReturns403AndJsonBody_throwsWithResponseBodyMessage() {
    val t = catchThrowable { client!!.get("/").aggregate().join() }
    assertThat(t).isInstanceOf(CompletionException::class.java)
    assertThat(t.cause)
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Sadness.")
  }
}
