/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class DependenciesTest {

  DependencyLink dl1 = new DependencyLink("Gizmoduck", "tflock", 4);
  DependencyLink dl2 = new DependencyLink("mobileweb", "Gizmoduck", 4);
  DependencyLink dl3 = new DependencyLink("tfe", "mobileweb", 2);
  DependencyLink dl4 = new DependencyLink("tfe", "mobileweb", 4);

  Dependencies deps1 = new Dependencies(0L, MICROSECONDS.convert(1, HOURS), asList(dl1, dl3));
  Dependencies deps2 = new Dependencies(MICROSECONDS.convert(1, HOURS), MICROSECONDS.convert(2, HOURS), asList(dl2, dl4));

  @Test
  public void identityOnDependenciesZERO() {
    assertThat(deps1.merge(Dependencies.ZERO))
        .isEqualTo(deps1);
    assertThat(Dependencies.ZERO.merge(deps1))
        .isEqualTo(deps1);
  }

  @Test
  public void sumsWhereParentChildMatch() {
    Dependencies result = deps1.merge(deps2);
    assertThat(result.startTs).isEqualTo(deps1.startTs);
    assertThat(result.endTs).isEqualTo(deps2.endTs);
    assertThat(result.links).containsOnly(
        dl1,
        dl2,
        new DependencyLink(dl3.parent, dl3.child, dl3.callCount + dl4.callCount)
    );
  }
}
