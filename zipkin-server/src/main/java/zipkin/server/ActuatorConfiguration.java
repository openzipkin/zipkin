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
package zipkin.server;

import com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.beans.BeansEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.condition.ConditionsReportEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.context.properties.ConfigurationPropertiesReportEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.env.EnvironmentEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.logging.LoggersEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.HeapDumpWebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.ThreadDumpEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import zipkin.server.ZipkinServer;

/**
 * Actuator relies on classpath scanning, so we cannot choose one configuration entrypoint unless
 * using {@link EnableAutoConfiguration}.
 *
 * <p>This cherry-picks the most relevant endpoints for when actuator is in the classpath because
 * we disable scanning in {@link ZipkinServer} by default.
 */
@Configuration
@ConditionalOnClass({
  ArmeriaSpringActuatorAutoConfiguration.class
})
@ImportAutoConfiguration({
  ArmeriaSpringActuatorAutoConfiguration.class,
  BeansEndpointAutoConfiguration.class,
  ConditionsReportEndpointAutoConfiguration.class,
  ConfigurationPropertiesReportEndpointAutoConfiguration.class,
  EndpointAutoConfiguration.class,
  EnvironmentEndpointAutoConfiguration.class,
  HeapDumpWebEndpointAutoConfiguration.class,
  LoggersEndpointAutoConfiguration.class,
  ThreadDumpEndpointAutoConfiguration.class
})
class ActuatorConfiguration {
}
