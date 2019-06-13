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
package zipkin2.internal;

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
@Threads(1)
public class ReadBufferBenchmarks {
  byte[] longBuff = {
    (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
    (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
  };

  @Benchmark public long readLong() {
    int pos = 0;
    return (longBuff[pos] & 0xffL) << 56
      | (longBuff[pos + 1] & 0xffL) << 48
      | (longBuff[pos + 2] & 0xffL) << 40
      | (longBuff[pos + 3] & 0xffL) << 32
      | (longBuff[pos + 4] & 0xffL) << 24
      | (longBuff[pos + 5] & 0xffL) << 16
      | (longBuff[pos + 6] & 0xffL) << 8
      | (longBuff[pos + 7] & 0xffL);
  }

  @Benchmark public long readLong_localArray() {
    int pos = 0;
    byte[] longBuff = this.longBuff;
    return (longBuff[pos] & 0xffL) << 56
      | (longBuff[pos + 1] & 0xffL) << 48
      | (longBuff[pos + 2] & 0xffL) << 40
      | (longBuff[pos + 3] & 0xffL) << 32
      | (longBuff[pos + 4] & 0xffL) << 24
      | (longBuff[pos + 5] & 0xffL) << 16
      | (longBuff[pos + 6] & 0xffL) << 8
      | (longBuff[pos + 7] & 0xffL);
  }

  @Benchmark public long readLong_8arity_localArray() {
    int pos = 0;
    return readLong(
      longBuff[pos] & 0xff,
      longBuff[pos + 1] & 0xff,
      longBuff[pos + 2] & 0xff,
      longBuff[pos + 3] & 0xff,
      longBuff[pos + 4] & 0xff,
      longBuff[pos + 5] & 0xff,
      longBuff[pos + 6] & 0xff,
      longBuff[pos + 7] & 0xff
    );
  }

  @Benchmark public long readLong_8arity() {
    int pos = 0;
    byte[] longBuff = this.longBuff;
    return readLong(
      longBuff[pos] & 0xff,
      longBuff[pos + 1] & 0xff,
      longBuff[pos + 2] & 0xff,
      longBuff[pos + 3] & 0xff,
      longBuff[pos + 4] & 0xff,
      longBuff[pos + 5] & 0xff,
      longBuff[pos + 6] & 0xff,
      longBuff[pos + 7] & 0xff
    );
  }

  static long readLong(int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7) {
    return (p0 & 0xffL) << 56
      | (p1 & 0xffL) << 48
      | (p2 & 0xffL) << 40
      | (p3 & 0xffL) << 32
      | (p4 & 0xffL) << 24
      | (p5 & 0xffL) << 16
      | (p6 & 0xffL) << 8
      | (p7 & 0xffL);
  }

  @Benchmark public long readLongReverseBytes() {
    return Long.reverseBytes(readLong());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + ReadBufferBenchmarks.class.getSimpleName() + ".*")
      .addProfiler("gc")
      .build();

    new Runner(opt).run();
  }
}
