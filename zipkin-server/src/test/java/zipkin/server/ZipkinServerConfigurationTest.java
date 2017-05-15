/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.buffer.CounterBuffers;
import org.springframework.boot.actuate.metrics.buffer.GaugeBuffers;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

public class ZipkinServerConfigurationTest
{
  AnnotationConfigApplicationContext context;

  @Before
  public void init()
  {
    context = new AnnotationConfigApplicationContext();
  }

  @After
  public void close()
  {
    if (context != null)
    {
      context.close();
    }
  }

  @Test
  public void ActuateCollectorMetrics_buffersAreNotPresent()
  {
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinServerConfigurationTest.Config.class, ZipkinServerConfiguration.class);
    context.refresh();

    assertBeanNotPresent(CounterBuffers.class);
    assertBeanNotPresent(GaugeBuffers.class);

    assertMetrics();
  }

  @Test
  public void ActuateCollectorMetrics_buffersArePresent()
  {
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinServerConfigurationTest.ConfigWithBuffers.class, ZipkinServerConfiguration.class);
    context.refresh();

    assertThat(context.getBean(CounterBuffers.class), notNullValue());
    assertThat(context.getBean(GaugeBuffers.class), notNullValue());

    assertMetrics();
  }

  private void assertMetrics()
  {
    ActuateCollectorMetrics metrics = context.getBean(ActuateCollectorMetrics.class);
    metrics.incrementBytes(20);
    assertThat(findMetric(metrics, "gauge.zipkin_collector.message_bytes").getValue(), equalTo(20.0d));
  }

  private Metric<?> findMetric(ActuateCollectorMetrics metrics, String metricName)
  {
    return metrics.metrics().stream().filter(m -> m.getName().equals(metricName)).findAny().get();
  }

  private void assertBeanNotPresent(Class<?> beanClass)
  {
    try
    {
      context.getBean(beanClass);
      fail("Unexpected bean for :" + beanClass);
    }
    catch (NoSuchBeanDefinitionException ex)
    {
      // expected
    }
  }

  private void assertBeanPresent(Class<?> beanClass)
  {
    assertThat(context.getBean(beanClass), notNullValue());
  }

  @Configuration
  public static class Config
  {
    @Bean
    public HealthAggregator healthAggregator()
    {
      return new OrderedHealthAggregator();
    }
  }

  @Configuration
  @Import(Config.class)
  public static class ConfigWithBuffers
  {
    @Bean
    public CounterBuffers counterBuffers()
    {
      return new CounterBuffers();
    }

    @Bean
    public GaugeBuffers gaugeBuffers()
    {
      return new GaugeBuffers();
    }
  }
}
