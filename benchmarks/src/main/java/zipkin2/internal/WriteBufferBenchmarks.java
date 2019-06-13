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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class WriteBufferBenchmarks {
  static final Charset UTF_8 = Charset.forName("UTF-8");
  // Order id = d07c4daa-0fa9-4c03-90b1-e06c4edae250 doesn't exist
  static final String CHINESE_UTF8 = "订单d07c4daa-0fa9-4c03-90b1-e06c4edae250不存在";
  static final int CHINESE_UTF8_SIZE = UTF_8.encode(CHINESE_UTF8).remaining();
  /* length-prefixing a 1 KiB span */
  static final int TEST_INT = 1024;
  /* epoch micros timestamp */
  static final long TEST_LONG = 1472470996199000L;
  byte[] bytes = new byte[8];
  WriteBuffer buffer = WriteBuffer.wrap(bytes);

  @Benchmark public int utf8SizeInBytes_chinese() {
    return WriteBuffer.utf8SizeInBytes(CHINESE_UTF8);
  }

  @Benchmark public byte[] writeUtf8_chinese() {
    byte[] bytesUtf8 = new byte[CHINESE_UTF8_SIZE];
    WriteBuffer.wrap(bytesUtf8, 0).writeUtf8(CHINESE_UTF8);
    return bytesUtf8;
  }

  @Benchmark public ByteBuffer writeUtf8_chinese_jdk() {
    return UTF_8.encode(CHINESE_UTF8);
  }

  @Benchmark public int varIntSizeInBytes_32() {
    return WriteBuffer.varintSizeInBytes(TEST_INT);
  }

  @Benchmark public int varIntSizeInBytes_64() {
    return WriteBuffer.varintSizeInBytes(TEST_LONG);
  }

  @Benchmark public int writeVarint_32() {
    buffer.writeVarint(TEST_INT);
    return buffer.pos();
  }

  @Benchmark public int writeVarint_64() {
    buffer.writeVarint(TEST_LONG);
    return buffer.pos();
  }

  @Benchmark public int writeLongLe() {
    buffer.writeLongLe(TEST_LONG);
    return buffer.pos();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + WriteBufferBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
