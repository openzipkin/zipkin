/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal

import com.linecorp.armeria.client.ClientFactoryBuilder
import com.linecorp.armeria.client.HttpClient
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.SessionProtocol
import com.linecorp.armeria.server.Server
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer

/**
 * This code ensures you can setup SSL.
 *
 *
 * This is inspired by com.linecorp.armeria.spring.ArmeriaSslConfigurationTest
 */
@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.config.name=zipkin-server",
    "armeria.ssl.enabled=true",
    "armeria.ports[1].port=0",
    "armeria.ports[1].protocols[0]=https",
    // redundant in zipkin-server-shared https://github.com/spring-projects/spring-boot/issues/16394
    "armeria.ports[0].port=\${server.port}",
    "armeria.ports[0].protocols[0]=http"
  ]
)
@RunWith(SpringRunner::class)
class ITZipkinServerSsl {
  @Autowired lateinit var server: Server

  // We typically use OkHttp in our tests, but Armeria bundles a handy insecure trust manager
  internal val clientFactory = ClientFactoryBuilder()
    .sslContextCustomizer { b -> b.trustManager(InsecureTrustManagerFactory.INSTANCE) }
    .build()

  @Test fun callHealthEndpoint_HTTP() {
    callHealthEndpoint(SessionProtocol.HTTP)
  }

  @Test fun callHealthEndpoint_HTTPS() {
    callHealthEndpoint(SessionProtocol.HTTPS)
  }

  fun callHealthEndpoint(protocol: SessionProtocol) {
    val response = HttpClient.of(clientFactory, Http.url(server, "", protocol)).get("/health")
      .aggregate().join()

    assertThat(response.status()).isEqualTo(HttpStatus.OK)
  }
}
