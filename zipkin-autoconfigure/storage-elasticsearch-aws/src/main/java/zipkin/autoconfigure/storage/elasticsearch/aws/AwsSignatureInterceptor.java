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
import com.amazonaws.ReadLimitInfo;
import com.amazonaws.RequestClientOptions;
import com.amazonaws.SignableRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.HttpMethodName;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.apache.http.*;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs aws v4 signing on an apache http request
 */
final class AwsSignatureInterceptor implements HttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(AwsSignatureInterceptor.class);

  private final String serviceName;
  private final String region;
  private final AWSCredentialsProvider credentialsProvider;

  public AwsSignatureInterceptor(String serviceName, String region, AWSCredentialsProvider credentialsProvider) {
    this.serviceName = serviceName;
    this.region = region;
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public void process(HttpRequest hr, HttpContext hc) throws HttpException, IOException {
    AWSCredentials creds;
    try {
      creds = credentialsProvider.getCredentials();
    } catch (AmazonClientException ace) {
      log.debug("Unable to load AWS credentials", ace);
      return;
    }

    AWS4Signer signer = new AWS4Signer();
    signer.setServiceName(serviceName);
    signer.setRegionName(region);

    signer.sign(new SignableHttpRequest(hr, hc), creds);
  }

  private static final class SignableHttpRequest implements SignableRequest<Object> {
    private final HttpRequestWrapper hr;
    private final HttpClientContext hc;
    // read your writes lol
    private final Map<String, String> signingHeaders = new HashMap<>();

    private SignableHttpRequest(HttpRequest hr, HttpContext hc) {
      this.hr = hr instanceof HttpRequestWrapper ? (HttpRequestWrapper) hr : HttpRequestWrapper.wrap(hr);
      this.hc = HttpClientContext.adapt(hc);
    }

    @Override
    public void addHeader(String name, String value) {
      hr.addHeader(name, value);
      signingHeaders.put(name, value);
    }

    @Override public Map<String, String> getHeaders() { return signingHeaders; }

    @Override public String getResourcePath() { return hr.getURI().getRawPath(); }

    @Override public void addParameter(String name, String value) { throw new UnsupportedOperationException(); }

    @Override public Map<String, List<String>> getParameters() { return parseQueryParams(hr.getURI()); }

    @Override
    public URI getEndpoint() {
      try {
        return new URI(HttpClientContext.adapt(hc).getTargetHost().toURI());
      } catch (URISyntaxException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override public HttpMethodName getHttpMethod() { return HttpMethodName.valueOf(hr.getMethod()); }

    @Override public int getTimeOffset() { return 0; }

    @Override
    public InputStream getContent() {
      try {
        return extractBody(hr);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public InputStream getContentUnwrapped() {
      try {
        return extractBody(hr);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public ReadLimitInfo getReadLimitInfo() {
      return new ReadLimitInfo() {
        @Override
        public int getReadLimit() {
          return RequestClientOptions.DEFAULT_STREAM_BUFFER_SIZE;
        }
      };
    }

    @Override public Object getOriginalRequestObject() { throw new UnsupportedOperationException(); }

    @Override public void setContent(InputStream in) { throw new UnsupportedOperationException(); }

    private static Map<String, List<String>> parseQueryParams(URI uri) {
      Map<String, List<String>> params = new HashMap<>();
      for (NameValuePair pair : URLEncodedUtils.parse(uri, Charsets.UTF_8.name())) {
        params.put(pair.getName(), Lists.newArrayList(pair.getValue()));
      }

      return params;
    }

    private static InputStream extractBody(HttpRequest request) throws IOException {
      if (request instanceof HttpEntityEnclosingRequest) {
        HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
        return entity != null ? entity.getContent() : null;
      } else {
        return null;
      }
    }
  }
}
