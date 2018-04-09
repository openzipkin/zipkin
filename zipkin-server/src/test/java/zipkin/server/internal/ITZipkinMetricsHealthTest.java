package zipkin.server.internal;

import com.jayway.jsonpath.JsonPath;
import io.prometheus.client.Histogram;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.V2SpanConverter;
import zipkin.server.ZipkinServer;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static zipkin.TestObjects.LOTS_OF_SPANS;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "spring.config.name=zipkin-server"
)
@RunWith(SpringRunner.class)
public class ITZipkinMetricsHealthTest {

  @Autowired InMemoryStorage storage;
  @Autowired ActuateCollectorMetrics metrics;
  @Autowired Histogram duration;
  @Value("${local.server.port}") int zipkinPort;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Before public void init() {
    storage.clear();
    duration.clear();
    metrics.forTransport("http").clear();
    Span span = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]);

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(asList(
      V2SpanConverter.fromSpan(span).get(0)
    ));
    post("/api/v2/spans", message);
  }

  @Test public void readsHealth() throws Exception{
    Response response = get("/health");
    assertThat(response.isSuccessful()).isTrue();
    String json = response.body().string();
    assertThat(readString("$status", json))
      .isEqualTo("UP");
    assertThat(readString("$zipkin.status", json))
      .isEqualTo("UP");
  }

  @Test public void readMetrics(){
    //TODO: Implement to read the metrics format
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .build()).execute();
  }

  static String readString(String path, String json){
    return JsonPath.compile("$status").read(json);
  }


}
