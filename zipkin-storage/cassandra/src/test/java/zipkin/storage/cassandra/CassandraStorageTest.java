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
package zipkin.storage.cassandra;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import org.junit.Test;
import zipkin.Component.CheckResult;

import static org.assertj.core.api.Assertions.assertThat;

public class CassandraStorageTest {

  @Test
  public void check_failsInsteadOfThrowing() {
    CheckResult result =
        CassandraStorage.builder().contactPoints("1.1.1.1").build().check();

    assertThat(result.ok).isFalse();
    assertThat(result.exception)
        .isInstanceOf(NoHostAvailableException.class);
  }
}
