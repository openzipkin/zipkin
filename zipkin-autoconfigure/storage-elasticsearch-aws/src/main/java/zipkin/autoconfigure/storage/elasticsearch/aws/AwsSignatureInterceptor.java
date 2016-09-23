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

import com.amazonaws.ReadLimitInfo;
import com.amazonaws.RequestClientOptions;
import com.amazonaws.SignableRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.HttpMethodName;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs aws v4 signing on an apache http request
 */
final class AwsSignatureInterceptor implements HttpRequestInterceptor {
  static final Logger log = LoggerFactory.getLogger(AwsSignatureInterceptor.class);

  final AWS4Signer signer;
  final AWSCredentialsProvider credentialsProvider;

  AwsSignatureInterceptor(String serviceName, String region,
      AWSCredentialsProvider credentialsProvider) {
    this.signer = new AWS4Signer();
    this.signer.setServiceName(serviceName);
    this.signer.setRegionName(region);
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public void process(HttpRequest hr, HttpContext hc) {
    AWSCredentials creds;
    try {
      creds = credentialsProvider.getCredentials();
    } catch (RuntimeException ace) {
      log.debug("Unable to load AWS credentials", ace);
      return;
    }

    signer.sign(new SignableHttpRequest(hr, hc), creds);
  }

  private static final class SignableHttpRequest implements SignableRequest<Object> {
    final HttpRequestWrapper hr;
    final HttpClientContext hc;
    // read your writes lol
    final Map<String, String> signingHeaders = new LinkedHashMap<>();

    SignableHttpRequest(HttpRequest hr, HttpContext hc) {
      this.hr = hr instanceof HttpRequestWrapper
          ? (HttpRequestWrapper) hr
          : HttpRequestWrapper.wrap(hr);
      this.hc = HttpClientContext.adapt(hc);
    }

    @Override
    public void addHeader(String name, String value) {
      hr.addHeader(name, value);
      signingHeaders.put(name, value);
    }

    @Override public Map<String, String> getHeaders() {
      return signingHeaders;
    }

    @Override public String getResourcePath() {
      return hr.getURI().getRawPath();
    }

    @Override public void addParameter(String name, String value) {
      throw new UnsupportedOperationException();
    }

    @Override public Map<String, List<String>> getParameters() {
      Map<String, List<String>> params = new LinkedHashMap<>();
      for (NameValuePair pair : URLEncodedUtils.parse(hr.getURI(), Charsets.UTF_8.name())) {
        params.put(pair.getName(), Lists.newArrayList(pair.getValue()));
      }
      return params;
    }

    @Override
    public URI getEndpoint() {
      return URI.create(HttpClientContext.adapt(hc).getTargetHost().toURI());
    }

    @Override public HttpMethodName getHttpMethod() {
      return HttpMethodName.valueOf(hr.getMethod());
    }

    @Override public int getTimeOffset() {
      return 0;
    }

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

    @Override public Object getOriginalRequestObject() {
      throw new UnsupportedOperationException();
    }

    @Override public void setContent(InputStream in) {
      throw new UnsupportedOperationException();
    }

    static InputStream extractBody(HttpRequest request) throws IOException {
      if (request instanceof HttpEntityEnclosingRequest) {
        HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
        return entity != null ? entity.getContent() : null;
      } else {
        return null;
      }
    }
  }
}
