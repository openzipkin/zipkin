package zipkin2.codec;

import com.google.common.io.Resources;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okio.ByteString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.Span;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class ProtoCodecBenchmarks {

  static final byte[] clientSpanJsonV2 = read("/zipkin2-client.json");
  static final Span clientSpan = SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);

  @Param({"1", "10", "100", "1000", "10000"})
  public int num;

  private byte[] encodedBytes;
  private ByteBuf encodedBuf;

  @Setup
  public void setup() {
    List<Span> spans = Collections.nCopies(num, clientSpan);
    encodedBytes = SpanBytesEncoder.PROTO3.encodeList(spans);

    encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encodedBytes.length);
    encodedBuf.writeBytes(encodedBytes);
  }

  @TearDown
  public void tearDown() {
    encodedBuf.release();
  }

  @Benchmark
  public List<Span> bytes_zipkinDecoder() {
    return SpanBytesDecoder.PROTO3.decodeList(encodedBytes);
  }

  @Benchmark
  public List<Span> bytes_protobufDecoder() {
    return ProtobufSpanDecoder.decodeList(encodedBytes);
  }

  @Benchmark
  public List<zipkin2.proto3.Span> bytes_wireDecoder() throws IOException {
    return zipkin2.proto3.Span.ADAPTER.asRepeated().decode(encodedBytes);
  }


  @Benchmark
  public List<Span> bytebuffer_zipkinDecoder() {
    return SpanBytesDecoder.PROTO3.decodeList(ByteBufUtil.getBytes(encodedBuf));
  }

  @Benchmark
  public List<Span> bytebuffer_protobufDecoder() {
    return ProtobufSpanDecoder.decodeList(encodedBuf.nioBuffer());
  }

  @Benchmark
  public List<zipkin2.proto3.Span> bytebuffer_wireDecoder() throws IOException {
    return zipkin2.proto3.Span.ADAPTER.asRepeated().decode(ByteString.of(encodedBuf.nioBuffer()));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + ProtoCodecBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }

  static byte[] read(String resource) {
    try {
      return Resources.toByteArray(Resources.getResource(CodecBenchmarks.class, resource));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
