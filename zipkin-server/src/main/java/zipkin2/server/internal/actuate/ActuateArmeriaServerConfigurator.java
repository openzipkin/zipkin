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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.PathMapping;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.http.ActuatorMediaType;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvokerAdvisor;
import org.springframework.boot.actuate.endpoint.invoke.ParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.boot.actuate.endpoint.web.PathMapper;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toList;

@Configuration
@AutoConfigureAfter(EndpointAutoConfiguration.class)
@EnableConfigurationProperties(WebEndpointProperties.class)
public class ActuateArmeriaServerConfigurator {

  private static final List<String> MEDIA_TYPES = Arrays
    .asList(ActuatorMediaType.V2_JSON, "application/json");

  private final ApplicationContext applicationContext;

  private final WebEndpointProperties properties;

  public ActuateArmeriaServerConfigurator(ApplicationContext applicationContext,
    WebEndpointProperties properties) {
    this.applicationContext = applicationContext;
    this.properties = properties;
  }

  @Bean @ConditionalOnMissingBean
  EndpointMediaTypes endpointMediaTypes() {
    return new EndpointMediaTypes(MEDIA_TYPES, MEDIA_TYPES);
  }

  @Bean @ConditionalOnMissingBean
  WebEndpointsSupplier webEndpointsSupplier(
    ParameterValueMapper parameterValueMapper,
    EndpointMediaTypes endpointMediaTypes,
    ObjectProvider<PathMapper> endpointPathMappers,
    ObjectProvider<OperationInvokerAdvisor> invokerAdvisors,
    ObjectProvider<EndpointFilter<ExposableWebEndpoint>> filters) {
    return new WebEndpointDiscoverer(this.applicationContext, parameterValueMapper,
      endpointMediaTypes,
      endpointPathMappers.orderedStream().collect(toList()),
      invokerAdvisors.orderedStream().collect(toList()),
      filters.orderedStream().collect(toList()));
  }

  @Bean ArmeriaServerConfigurator actuateArmeriaServerConfigurator(
    ObjectMapper objectMapper,
    WebEndpointsSupplier supplier,
    EndpointMediaTypes endpointMediaTypes,
    WebEndpointProperties properties) {

    EndpointMapping endpointMapping = new EndpointMapping(properties.getBasePath());
    Collection<ExposableWebEndpoint> endpoints = supplier.getEndpoints();
    return sb -> {
      endpoints.stream().flatMap((endpoint) -> endpoint.getOperations().stream())
        .forEach((operation) -> {
            PathMapping mapping = getPathMapping(
              operation.getRequestPredicate().getHttpMethod().name(),
              endpointMapping.createSubPath(operation.getRequestPredicate().getPath()),
              operation.getRequestPredicate().getConsumes(),
              operation.getRequestPredicate().getProduces()
            );
            sb.service(mapping, new WebOperationHttpService(objectMapper, operation));
          }
        );
      if (StringUtils.hasText(endpointMapping.getPath())) {
        PathMapping mapping = getPathMapping(
          HttpMethod.GET.name(),
          endpointMapping.getPath(),
          endpointMediaTypes.getConsumed(),
          endpointMediaTypes.getProduced()
        );
        sb.service(mapping, (ctx, req) -> {
          Map<String, Link> links = new EndpointLinksResolver(endpoints).resolveLinks(req.path());
          return HttpResponse.of(
            HttpStatus.OK,
            MediaType.JSON,
            objectMapper.writeValueAsBytes(Collections.singletonMap("_links", links))
          );
        });
      }
    };
  }

  static PathMapping getPathMapping(
    String method, String path, Collection<String> consumes, Collection<String> produces) {
    return new TolerantPathMapping(PathMapping.ofExact(path)
      .withHttpHeaderInfo(
        Collections.singleton(HttpMethod.valueOf(method)),
        consumes.stream().map(MediaType::parse).collect(toList()),
        produces.stream().map(MediaType::parse).collect(toList())
      ));
  }
}
