/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server.brave;

import com.datastax.driver.core.Session;
import com.github.kristofa.brave.Brave;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import zipkin.cassandra.CassandraStorage;
import zipkin.cassandra.SessionFactory;
import zipkin.server.ConditionalOnSelfTracing;

/** Sets up the Cassandra tracing in Brave as an initialization. */
@ConditionalOnSelfTracing(storageType = "cassandra")
@Configuration
public class CassandraTracerConfiguration {
  final SessionFactory delegate = new SessionFactory.Default();

  // Lazy to unwind a circular dep: we are tracing the storage used by brave
  @Autowired @Lazy Brave brave;
  @Autowired @Lazy LocalSpanCollector collector;

  @Bean SessionFactory tracingSessionFactory() {
    return new SessionFactory() {
      @Override public Session create(CassandraStorage storage) {
        return TracedSession.create(delegate.create(storage), brave, collector);
      }
    };
  }
}
