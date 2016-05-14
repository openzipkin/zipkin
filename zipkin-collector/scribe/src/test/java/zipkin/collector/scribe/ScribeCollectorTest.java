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
package zipkin.collector.scribe;

import org.jboss.netty.channel.ChannelException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Component.CheckResult;
import zipkin.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;

public class ScribeCollectorTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void check_failsWhenNotStarted() {
    try (ScribeCollector scribe =
             ScribeCollector.builder().storage(new InMemoryStorage()).port(12345).build()) {

      CheckResult result = scribe.check();
      assertThat(result.ok).isFalse();
      assertThat(result.exception)
          .isInstanceOf(IllegalStateException.class);

      scribe.start();
      assertThat(scribe.check().ok).isTrue();
    }
  }

  @Test
  public void start_failsWhenCantBindPort() {
    thrown.expect(ChannelException.class);
    thrown.expectMessage("Failed to bind to: 0.0.0.0/0.0.0.0:12345");

    ScribeCollector.Builder builder =
        ScribeCollector.builder().storage(new InMemoryStorage()).port(12345);

    try (ScribeCollector first = builder.build().start()) {
      try (ScribeCollector samePort = builder.build().start()) {
      }
    }
  }
}
