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
package zipkin2;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class EndpointBenchmarks {
  static final String IPV4 = "43.0.192.2", IPV6 = "2001:db8::c001";
  static final InetAddress IPV4_ADDR, IPV6_ADDR;

  static {
    try {
      IPV4_ADDR = Inet4Address.getByName(IPV4);
      IPV6_ADDR = Inet6Address.getByName(IPV6);
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
  }

  Endpoint.Builder builder = Endpoint.newBuilder();

  @Benchmark public boolean parseIpv4_literal() {
    return builder.parseIp(IPV4);
  }

  @Benchmark public boolean parseIpv4_addr() {
    return builder.parseIp(IPV4_ADDR);
  }

  @Benchmark public boolean parseIpv4_bytes() {
    return builder.parseIp(IPV4_ADDR.getAddress());
  }

  @Benchmark public boolean parseIpv6_literal() {
    return builder.parseIp(IPV6);
  }

  @Benchmark public boolean parseIpv6_addr() {
    return builder.parseIp(IPV6_ADDR);
  }

  @Benchmark public boolean parseIpv6_bytes() {
    return builder.parseIp(IPV6_ADDR.getAddress());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + EndpointBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
