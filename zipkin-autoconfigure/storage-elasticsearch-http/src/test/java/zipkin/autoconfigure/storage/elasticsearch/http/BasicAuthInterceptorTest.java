package zipkin.autoconfigure.storage.elasticsearch.http;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BasicAuthInterceptorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockWebServer mockWebServer;
  private OkHttpClient client;

  @Before
  public void beforeEach() {
    BasicAuthInterceptor interceptor =
        new BasicAuthInterceptor(new ZipkinElasticsearchHttpStorageProperties());
    client = new OkHttpClient.Builder().addNetworkInterceptor(interceptor).build();
    mockWebServer = new MockWebServer();
  }

  @After
  public void afterEach() throws IOException {
    client.dispatcher().executorService().shutdownNow();
    mockWebServer.close();
  }

  @Test
  public void intercept_whenESReturns403AndJsonBody_throwsWithResponseBodyMessage()
      throws Exception {

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Sadness.");

    mockWebServer.enqueue(new MockResponse().setResponseCode(403)
        .setBody("{\"message\":\"Sadness.\"}"));

    client.newCall(new Request.Builder().url(mockWebServer.url("/")).build())
        .execute();
  }
}