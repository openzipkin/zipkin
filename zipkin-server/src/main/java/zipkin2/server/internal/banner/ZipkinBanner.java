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
package zipkin2.server.internal.banner;

import org.springframework.boot.ResourceBanner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

public class ZipkinBanner extends ResourceBanner {

  public ZipkinBanner() {
    super(new ClassPathResource("zipkin.txt"));
  }

  @Override
  protected List<PropertyResolver> getPropertyResolvers(Environment environment, Class<?> sourceClass) {
    final List<PropertyResolver> propertyResolvers = super.getPropertyResolvers(environment, sourceClass);
    propertyResolvers.add(getZipkinAnsi256Resolver());
    return propertyResolvers;
  }

  private PropertyResolver getZipkinAnsi256Resolver() {
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(new ZipkinAnsi256PropertySource("zipkinAnsi256"));
    return new PropertySourcesPropertyResolver(sources);
  }
}
