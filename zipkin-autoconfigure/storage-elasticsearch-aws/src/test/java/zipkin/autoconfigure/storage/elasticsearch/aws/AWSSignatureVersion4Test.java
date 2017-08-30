/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class AWSSignatureVersion4Test {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  @Rule
  public MockWebServer es = new MockWebServer();

  String region = "us-east-1";
  AWSCredentials.Provider credentials =
      () -> new AWSCredentials("access-key", "secret-key", null);

  AWSSignatureVersion4 signer = new AWSSignatureVersion4(region, "es", () -> credentials.get());

  OkHttpClient client = new OkHttpClient.Builder().addNetworkInterceptor(signer).build();

  @After
  public void close() throws IOException {
    client.dispatcher().executorService().shutdownNow();
  }

  @Test
  public void propagatesExceptionGettingCredentials() throws InterruptedException, IOException {
    // makes sure this isn't wrapped.
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Unable to load AWS credentials from any provider in the chain");

    credentials = () -> {
      throw new IllegalStateException(
          "Unable to load AWS credentials from any provider in the chain");
    };

    client.newCall(new Request.Builder().url(es.url("/")).build()).execute();
  }

  @Test
  public void unwrapsJsonError() throws InterruptedException, IOException {
    // makes sure this isn't wrapped.
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The request signature we calculated does not match the signature you provided.");

    es.enqueue(new MockResponse().setResponseCode(403)
        .setBody(
            "{\"message\":\"The request signature we calculated does not match the signature you provided.\"}"));

    client.newCall(new Request.Builder().url(es.url("/_template/zipkin_template")).build())
        .execute();
  }

  @Test
  public void signsRequestsForRegionAndEsService() throws InterruptedException, IOException {
    es.enqueue(new MockResponse());

    client.newCall(new Request.Builder().url(es.url("/_template/zipkin_template")).build())
        .execute();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getHeader("Authorization"))
        .startsWith("AWS4-HMAC-SHA256 Credential=" + credentials.get().accessKey)
        .contains(region + "/es/aws4_request"); // for the region and service
  }

  @Test
  public void canonicalString_commasInPath() throws InterruptedException, IOException {
    es.enqueue(new MockResponse());

    Request request = new Request.Builder()
        .header("host", "search-zipkin-2rlyh66ibw43ftlk4342ceeewu.ap-southeast-1.es.amazonaws.com")
        .header("x-amz-date", "20161004T132314Z")
        .url(es.url("zipkin-2016-10-05,zipkin-2016-10-06/dependencylink/_search?allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true"))
        .post(RequestBody.create(MediaType.parse("application/json"), "{\n"
            + "    \"query\" : {\n"
            + "      \"match_all\" : { }\n"
            + "    }")).build();

    // Ensure that the canonical string encodes commas with %2C
    assertThat(AWSSignatureVersion4.canonicalString(request).readUtf8())
        .isEqualTo("POST\n"
            + "/zipkin-2016-10-05%2Czipkin-2016-10-06/dependencylink/_search\n"
            + "allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true\n"
            + "host:search-zipkin-2rlyh66ibw43ftlk4342ceeewu.ap-southeast-1.es.amazonaws.com\n"
            + "x-amz-date:20161004T132314Z\n"
            + "\n"
            + "host;x-amz-date\n"
            + "2fd35cb36e5de91bbae279313c371fb630a6b3aab1478df378c5e73e667a1747");
  }

  /** Starting with Zipkin 1.31 colons are used to delimit index types in ES */
  @Test
  public void canonicalString_colonsInPath() throws InterruptedException, IOException {
    es.enqueue(new MockResponse());

    Request request = new Request.Builder()
      .header("host", "search-zipkin53-mhdyquzbwwzwvln6phfzr3mmdi.ap-southeast-1.es.amazonaws.com")
      .header("x-amz-date", "20170830T143137Z")
      .url(es.url("_cluster/health/zipkin:span-*"))
      .get().build();

    // Ensure that the canonical string encodes commas with %2C
    assertThat(AWSSignatureVersion4.canonicalString(request).readUtf8())
      .isEqualTo("GET\n"
        + "/_cluster/health/zipkin%3Aspan-%2A\n"
        + "\n"
        + "host:search-zipkin53-mhdyquzbwwzwvln6phfzr3mmdi.ap-southeast-1.es.amazonaws.com\n"
        + "x-amz-date:20170830T143137Z\n"
        + "\n"
        + "host;x-amz-date\n"
        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
