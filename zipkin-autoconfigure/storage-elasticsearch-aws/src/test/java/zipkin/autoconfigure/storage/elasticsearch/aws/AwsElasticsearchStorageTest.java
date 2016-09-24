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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.storage.elasticsearch.ElasticsearchStorage;
import zipkin.storage.elasticsearch.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

public class AwsElasticsearchStorageTest {
  @Rule
  public MockWebServer es = new MockWebServer();

  String region = "us-east-1";
  Supplier<AWSCredentials> credentials = () -> new BasicAWSCredentials("access-key", "secret-key");

  ElasticsearchAwsRequestSigner signer =
      new ElasticsearchAwsRequestSigner(region, new AWSCredentialsProvider() {
        @Override public AWSCredentials getCredentials() {
          return credentials.get();
        }

        @Override public void refresh() {
        }
      });

  ElasticsearchStorage storage = ElasticsearchStorage.builder(new HttpClient.Builder()
      .addPostInterceptor(signer)
      .hosts(ImmutableList.of(es.url("/").toString()))).build();

  @After
  public void close() throws IOException {
    storage.close();
  }

  @Test
  public void check_failsOnCredentialException() throws InterruptedException {
    credentials = () -> {
      throw new AmazonClientException(
          "Unable to load AWS credentials from any provider in the chain");
    };

    assertThat(storage.check().ok).isFalse();
    assertThat(storage.check().exception) // notably, this isn't wrapped
        .isInstanceOf(AmazonClientException.class);
  }

  @Test
  public void signsRequestsForRegionAndEsService() throws InterruptedException {
    // check lazy calls get template, then checks cluster status.
    // We need to enqueue both or the test will hang.
    es.enqueue(new MockResponse()); // GET /_template/zipkin_template
    es.enqueue(new MockResponse().setBody("{\n"
        + "  \"cluster_name\" : \"zipkin\",\n"
        + "  \"status\" : \"green\"\n"
        + "}")); // GET /_cluster/health/zipkin-*

    assertThat(storage.check().ok).isTrue();

    RecordedRequest request = es.takeRequest(); // spot-check the first request
    assertThat(request.getHeader("Authorization"))
        .startsWith("AWS4-HMAC-SHA256 Credential=" + credentials.get().getAWSAccessKeyId())
        .contains(region + "/es/aws4_request"); // for the region and service
  }
}
