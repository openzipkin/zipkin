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

import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zipkin.storage.elasticsearch.aws")
public class ZipkinElasticsearchAwsStorageProperties implements Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  /** The name of a domain to look up by endpoint. Exclusive with hosts list. */
  private String domain;

  /**
   * The optional region to search for the domain {@link #domain}. Defaults the usual
   * way (AWS_REGION, DEFAULT_AWS_REGION, etc.).
   */
  private String region;

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = "".equals(domain) ? null : domain;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = "".equals(region) ? null : region;
  }
}
