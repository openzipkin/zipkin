/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal;

import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import java.net.InetSocketAddress;
import org.junit.Test;
import zipkin2.internal.Nullable;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zipkin2.storage.cassandra.internal.SessionBuilder.parseContactPoints;

public class SessionBuilderTest {
  @Test public void contactPoints_defaultsToLocalhost() {
    assertThat(parseContactPoints("localhost"))
      .containsExactly(new InetSocketAddress("127.0.0.1", 9042));
  }

  @Test public void contactPoints_defaultsToPort9042() {
    assertThat(parseContactPoints("1.1.1.1"))
      .containsExactly(new InetSocketAddress("1.1.1.1", 9042));
  }

  @Test public void contactPoints_defaultsToPort9042_multi() {
    assertThat(parseContactPoints("1.1.1.1:9143,2.2.2.2"))
      .containsExactly(
        new InetSocketAddress("1.1.1.1", 9143), new InetSocketAddress("2.2.2.2", 9042));
  }

  @Test public void contactPoints_hostAndPort() {
    assertThat(parseContactPoints("1.1.1.1:9142"))
      .containsExactly(new InetSocketAddress("1.1.1.1", 9142));
  }

  @Test public void connectPort_singleContactPoint() {
    assertThat(buildCluster("1.1.1.1:9142", null).getConfiguration().getProtocolOptions().getPort())
      .isEqualTo(9142);
  }

  @Test public void connectPort_whenContactPointsHaveSamePort() {
    assertThat(
      buildCluster("1.1.1.1:9143,2.2.2.2:9143", null).getConfiguration()
        .getProtocolOptions()
        .getPort())
      .isEqualTo(9143);
  }

  @Test public void connectPort_whenContactPointsHaveMixedPorts_coercesToDefault() {
    assertThat(
      buildCluster("1.1.1.1:9143,2.2.2.2", null).getConfiguration().getProtocolOptions().getPort())
      .isEqualTo(9042);
  }

  @Test public void loadBalancing_defaultsToRoundRobin() {
    RoundRobinPolicy policy = toRoundRobinPolicy();

    Host foo = mock(Host.class);
    when(foo.getDatacenter()).thenReturn("foo");
    Host bar = mock(Host.class);
    when(bar.getDatacenter()).thenReturn("bar");
    policy.init(mock(Cluster.class), asList(foo, bar));

    assertThat(policy.distance(foo)).isEqualTo(HostDistance.LOCAL);
    assertThat(policy.distance(bar)).isEqualTo(HostDistance.LOCAL);
  }

  RoundRobinPolicy toRoundRobinPolicy() {
    return (RoundRobinPolicy)
      ((LatencyAwarePolicy)
        ((TokenAwarePolicy)
          buildCluster("1.1.1.1", null)
            .getConfiguration()
            .getPolicies()
            .getLoadBalancingPolicy())
          .getChildPolicy())
        .getChildPolicy();
  }

  @Test public void loadBalancing_settingLocalDcIgnoresOtherDatacenters() {
    DCAwareRoundRobinPolicy policy = toDCAwareRoundRobinPolicy("bar");

    Host foo = mock(Host.class);
    when(foo.getDatacenter()).thenReturn("foo");
    Host bar = mock(Host.class);
    when(bar.getDatacenter()).thenReturn("bar");
    policy.init(mock(Cluster.class), asList(foo, bar));

    assertThat(policy.distance(foo)).isEqualTo(HostDistance.IGNORED);
    assertThat(policy.distance(bar)).isEqualTo(HostDistance.LOCAL);
  }

  DCAwareRoundRobinPolicy toDCAwareRoundRobinPolicy(String localDc) {
    return (DCAwareRoundRobinPolicy)
      ((LatencyAwarePolicy)
        ((TokenAwarePolicy)
          buildCluster("1.1.1.1", localDc)
            .getConfiguration()
            .getPolicies()
            .getLoadBalancingPolicy())
          .getChildPolicy())
        .getChildPolicy();
  }

  static Cluster buildCluster(String contactPoints, @Nullable String localDc) {
    return SessionBuilder.buildCluster(contactPoints, localDc, new PoolingOptions(),
      AuthProvider.NONE, false);
  }
}
