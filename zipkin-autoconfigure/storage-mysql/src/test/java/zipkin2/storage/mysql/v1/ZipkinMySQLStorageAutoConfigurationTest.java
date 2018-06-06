/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage.mysql.v1;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin.autoconfigure.storage.mysql.Access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinMySQLStorageAutoConfigurationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesntProvidesStorageComponent_whenStorageTypeNotMySQL() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:cassandra");
    Access.registerMySQL(context);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(MySQLStorage.class);
  }

  @Test
  public void providesStorageComponent_whenStorageTypeMySQL() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:mysql");
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class)).isNotNull();
  }

  @Test
  public void canOverridesProperty_username() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
      "zipkin.storage.type:mysql",
      "zipkin.storage.mysql.username:robot"
    );
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getUsername())
      .isEqualTo("robot");
  }

  @Test
  public void strictTraceId_defaultsToTrue() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:mysql");
    Access.registerMySQL(context);
    context.refresh();
    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isTrue();
  }

  @Test
  public void strictTraceId_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:mysql");
    addEnvironment(context, "zipkin.storage.strict-trace-id:false");
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isFalse();
  }
}
